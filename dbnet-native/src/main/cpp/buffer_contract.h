#pragma once
#include <cstddef>
namespace dbnet {
inline bool valid_input(int w, int h, int length) {
    if (w <= 0 || h <= 0 || w > 1024 || h > 1024 || w % 256 || h % 256) return false;
    const std::size_t area = static_cast<std::size_t>(w) * h;
    return length >= 0 && static_cast<std::size_t>(length) == 3 * area;
}
inline bool valid_output(int dims, int w, int h, int d, int c, std::size_t element_size, int pack,
                         int length, std::size_t input_area) {
    // Accept CHW or a single 2D mask plane, never a depth axis or a rank-2 DB tensor.
    const bool supported_rank = dims == 3 || (dims == 2 && c == 1);
    if (!supported_rank || d != 1 || w <= 0 || h <= 0 || w > 1024 || h > 1024 || c < 1 || c > 2 ||
        element_size != sizeof(float) || pack != 1 || length < 0) return false;
    const std::size_t area = static_cast<std::size_t>(w) * h;
    return area <= input_area && area * c <= static_cast<std::size_t>(length);
}
}
