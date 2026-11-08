cmake_minimum_required(VERSION 3.22.1)
project(stockfish LANGUAGES CXX)

set(STOCKFISH_SRC "${CMAKE_CURRENT_SOURCE_DIR}/stockfish/src")

add_library(stockfish SHARED
    jni/stockfish_jni.cpp
    "${STOCKFISH_SRC}/attacks.cpp"
    "${STOCKFISH_SRC}/benchmark.cpp"
    "${STOCKFISH_SRC}/bitboard.cpp"
    "${STOCKFISH_SRC}/evaluate.cpp"
    "${STOCKFISH_SRC}/misc.cpp"
    "${STOCKFISH_SRC}/movegen.cpp"
    "${STOCKFISH_SRC}/movepick.cpp"
    "${STOCKFISH_SRC}/position.cpp"
    "${STOCKFISH_SRC}/search.cpp"
    "${STOCKFISH_SRC}/thread.cpp"
    "${STOCKFISH_SRC}/timeman.cpp"
    "${STOCKFISH_SRC}/tt.cpp"
    "${STOCKFISH_SRC}/uci.cpp"
    "${STOCKFISH_SRC}/ucioption.cpp"
    "${STOCKFISH_SRC}/tune.cpp"
    "${STOCKFISH_SRC}/syzygy/tbprobe.cpp"
    "${STOCKFISH_SRC}/nnue/nnue_accumulator.cpp"
    "${STOCKFISH_SRC}/nnue/nnue_misc.cpp"
    "${STOCKFISH_SRC}/nnue/network.cpp"
    "${STOCKFISH_SRC}/nnue/features/half_ka_v2_hm.cpp"
    "${STOCKFISH_SRC}/nnue/features/full_threats.cpp"
    "${STOCKFISH_SRC}/nnue/features/pp_3wide.cpp"
    "${STOCKFISH_SRC}/engine.cpp"
    "${STOCKFISH_SRC}/score.cpp"
    "${STOCKFISH_SRC}/memory.cpp"
)

target_include_directories(stockfish PRIVATE "${STOCKFISH_SRC}")
target_compile_features(stockfish PRIVATE cxx_std_17)
target_compile_definitions(stockfish PRIVATE NNUE_EMBEDDING_OFF NDEBUG)
target_compile_options(stockfish PRIVATE
    -O2
    -fno-exceptions
    -Wall
    -Wextra
    -Wno-unused-parameter
)
target_link_libraries(stockfish PRIVATE log)
target_link_options(stockfish PRIVATE "-Wl,-z,max-page-size=16384")
