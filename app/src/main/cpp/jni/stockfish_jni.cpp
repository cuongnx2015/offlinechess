#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <condition_variable>
#include <filesystem>
#include <memory>
#include <mutex>
#include <queue>
#include <sstream>
#include <string>
#include <cstdlib>
#include <vector>

#include "engine.h"
#include "evaluate.h"
#include "score.h"
#include "search.h"
#include "ucioption.h"

namespace {

constexpr char LogTag[] = "OfflineChessStockfish";
constexpr char NetworkName[] = "nn-1a298aa575a0.nnue";
constexpr char StartFen[] = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

std::mutex engineMutex;
std::mutex outputMutex;
std::condition_variable outputAvailable;
std::queue<std::string> outputLines;
std::unique_ptr<Stockfish::Engine> engine;
bool outputClosed = false;
bool positionValid = true;
std::atomic_bool searching{false};

void emit(std::string line) {
    {
        std::lock_guard<std::mutex> lock(outputMutex);
        if (outputLines.size() >= 512) {
            while (!outputLines.empty() && outputLines.front().rfind("info ", 0) == 0) {
                outputLines.pop();
            }
            if (outputLines.size() >= 512) outputLines.pop();
        }
        outputLines.push(std::move(line));
    }
    outputAvailable.notify_one();
}

void closeOutput() {
    {
        std::lock_guard<std::mutex> lock(outputMutex);
        outputClosed = true;
    }
    outputAvailable.notify_all();
}

void finishSearch() {
    if (searching) engine->stop();
    engine->wait_for_search_finished();
    searching = false;
}

std::string toUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string scoreText(const Stockfish::Score& score) {
    if (score.is<Stockfish::Score::Mate>()) {
        const int plies = score.get<Stockfish::Score::Mate>().plies;
        const int moves = (std::abs(plies) + 1) / 2;
        return "mate " + std::to_string(plies < 0 ? -moves : moves);
    }
    if (score.is<Stockfish::Score::Tablebase>()) {
        const auto tb = score.get<Stockfish::Score::Tablebase>();
        return std::string("mate ") + (tb.win ? "" : "-") +
            std::to_string((std::abs(tb.plies) + 1) / 2);
    }
    return "cp " + std::to_string(score.get<Stockfish::Score::InternalUnits>().value);
}

int readValue(std::istringstream& stream, const std::string& name) {
    int value = 0;
    if (!(stream >> value) || value < 0) {
        emit("info string invalid value for " + name);
        return -1;
    }
    return value;
}

void setPosition(const std::string& command) {
    finishSearch();
    positionValid = false;
    const auto reject = [](const std::string& message) {
        emit("info string CRITICAL ERROR position " + message);
    };
    std::istringstream stream(command);
    std::string token;
    std::string fen;
    std::vector<std::string> moves;
    if (!(stream >> token)) {
        reject("command is incomplete");
        return;
    }
    if (token == "startpos") {
        fen = StartFen;
        if (stream >> token) {
            if (token != "moves") {
                reject("expected moves marker");
                return;
            }
            while (stream >> token) moves.push_back(token);
        }
    } else if (token == "fen") {
        std::vector<std::string> fields;
        bool hasMoves = false;
        while (stream >> token) {
            if (token == "moves") {
                hasMoves = true;
                break;
            }
            fields.push_back(token);
        }
        if (fields.size() != 6) {
            reject("FEN must contain six fields");
            return;
        }
        for (const auto& field : fields) {
            if (!fen.empty()) fen += ' ';
            fen += field;
        }
        if (hasMoves) while (stream >> token) moves.push_back(token);
    } else {
        reject("expected startpos or fen");
        return;
    }
    const auto error = engine->set_position(fen, moves);
    if (error) {
        reject(error->what());
        return;
    }
    positionValid = true;
}

void startSearch(const std::string& command) {
    if (!positionValid) {
        emit("info string CRITICAL ERROR search cancelled because the position was rejected");
        return;
    }
    finishSearch();
    Stockfish::Search::LimitsType limits;
    limits.startTime = Stockfish::now();
    std::istringstream stream(command);
    std::string token;
    stream >> token;
    while (stream >> token) {
        int value = 0;
        if (token == "infinite") {
            limits.infinite = 1;
        } else if (token == "ponder") {
            limits.ponderMode = true;
        } else if (token == "searchmoves") {
            while (stream >> token) {
                std::transform(token.begin(), token.end(), token.begin(), [](unsigned char ch) {
                    return static_cast<char>(std::tolower(ch));
                });
                limits.searchmoves.push_back(token);
            }
            break;
        } else {
            value = readValue(stream, token);
            if (value < 0) return;
            if (token == "depth") limits.depth = value;
            else if (token == "mate") limits.mate = value;
            else if (token == "nodes") limits.nodes = static_cast<Stockfish::u64>(value);
            else if (token == "movetime") limits.movetime = value;
            else if (token == "movestogo") limits.movestogo = value;
            else if (token == "wtime") limits.time[Stockfish::WHITE] = value;
            else if (token == "btime") limits.time[Stockfish::BLACK] = value;
            else if (token == "winc") limits.inc[Stockfish::WHITE] = value;
            else if (token == "binc") limits.inc[Stockfish::BLACK] = value;
            else if (token == "perft") limits.perft = value;
            else {
                emit("info string unsupported go parameter " + token);
                return;
            }
        }
    }
    searching = true;
    engine->go(limits);
}

void processCommand(const std::string& command) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!engine) {
        emit("info string engine is not initialized");
        return;
    }
    const auto separator = command.find(' ');
    const std::string verb = command.substr(0, separator);
    if (verb == "uci") {
        emit("id name Stockfish 19");
        emit("id author The Stockfish developers");
        emit("option name Threads type spin default 1 min 1 max 2");
        emit("option name Hash type spin default 16 min 1 max 64");
        emit("option name Skill Level type spin default 20 min 0 max 20");
        emit("option name MultiPV type spin default 1 min 1 max 5");
        emit("uciok");
    } else if (verb == "isready") {
        emit("readyok");
    } else if (verb == "ucinewgame") {
        finishSearch();
        engine->search_clear();
    } else if (verb == "position") {
        setPosition(command.substr(separator == std::string::npos ? command.size() : separator + 1));
    } else if (verb == "go") {
        startSearch(command);
    } else if (verb == "stop") {
        if (searching) engine->stop();
    } else if (verb == "setoption") {
        finishSearch();
        std::istringstream options(command.substr(separator + 1));
        engine->get_options().setoption(options);
    } else if (verb == "quit") {
        finishSearch();
        closeOutput();
    } else {
        emit("info string unsupported UCI command " + verb);
    }
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_offlinechess_engine_StockfishNative_nativeInitialize(
    JNIEnv* env,
    jclass,
    jstring executablePath) {
    const std::string path = toUtf8(env, executablePath);
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine) return JNI_TRUE;
    const auto enginePath = std::filesystem::u8path(path);
    const auto networkPath = enginePath.parent_path() / NetworkName;
    if (!std::filesystem::is_regular_file(networkPath)) {
        __android_log_print(ANDROID_LOG_ERROR, LogTag, "Stockfish NNUE is missing: %s", networkPath.c_str());
        return JNI_FALSE;
    }
    {
        std::lock_guard<std::mutex> outputLock(outputMutex);
        outputClosed = false;
        while (!outputLines.empty()) outputLines.pop();
    }

    engine = std::make_unique<Stockfish::Engine>(enginePath);
    positionValid = true;
    std::istringstream threadOption("name Threads value 1");
    engine->get_options().setoption(threadOption);
    std::istringstream hashOption("name Hash value 16");
    engine->get_options().setoption(hashOption);
    engine->set_on_start([] {});
    engine->set_on_update_no_moves([](const Stockfish::Engine::InfoShort& info) {
        emit("info depth " + std::to_string(info.depth) + " score " + scoreText(info.score));
    });
    engine->set_on_iter([](const Stockfish::Engine::InfoIter&) {});
    engine->set_on_update_full([](const Stockfish::Engine::InfoFull& info) {
        std::ostringstream line;
        line << "info depth " << info.depth
             << " seldepth " << info.selDepth
             << " multipv " << info.multiPV
             << " score " << scoreText(info.score)
             << " time " << info.timeMs
             << " nodes " << info.nodes
             << " nps " << info.nps
             << " hashfull " << info.hashfull
             << " pv " << info.pv;
        emit(line.str());
    });
    engine->set_on_bestmove([](std::string_view bestmove, std::string_view ponder) {
        std::string line = "bestmove " + std::string(bestmove);
        if (!ponder.empty()) line += " ponder " + std::string(ponder);
        {
            searching = false;
        }
        emit(std::move(line));
    });
    engine->set_on_verify_network([](std::string_view message) {
        emit("info string " + std::string(message));
    });
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlinechess_engine_StockfishNative_nativeSendCommand(
    JNIEnv* env,
    jclass,
    jstring command) {
    processCommand(toUtf8(env, command));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offlinechess_engine_StockfishNative_nativeReadOutput(
    JNIEnv* env,
    jclass) {
    std::unique_lock<std::mutex> lock(outputMutex);
    outputAvailable.wait(lock, [] { return outputClosed || !outputLines.empty(); });
    if (outputLines.empty()) return nullptr;
    std::string line = std::move(outputLines.front());
    outputLines.pop();
    return env->NewStringUTF(line.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlinechess_engine_StockfishNative_nativeShutdown(JNIEnv*, jclass) {
    {
        std::lock_guard<std::mutex> lock(engineMutex);
        if (engine) {
            finishSearch();
            engine.reset();
        }
    }
    {
        std::lock_guard<std::mutex> lock(outputMutex);
        outputClosed = true;
    }
    outputAvailable.notify_all();
}
