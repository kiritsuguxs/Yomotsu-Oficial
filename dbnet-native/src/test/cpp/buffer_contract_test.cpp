#include "buffer_contract.h"
#include <cassert>
#include <climits>
int main() {
    using namespace dbnet;
    assert(valid_input(768, 1024, 3 * 768 * 1024));
    assert(!valid_input(-1, 1024, 0));
    assert(!valid_input(960, 960, 3 * 960 * 960));
    assert(!valid_input(INT_MAX, INT_MAX, INT_MAX));
    assert(!valid_input(1024, 1024, 3 * 1024 * 1024 - 1));
    constexpr int area = 768 * 1024;
    assert(valid_output(3,768,1024,1,2,4,1,2*area,area));
    assert(valid_output(3,384,512,1,1,4,1,area,area));
    assert(valid_output(3,768,1024,1,1,4,1,area,area));
    assert(valid_output(2,384,512,1,1,4,1,area,area));
    assert(valid_output(2,768,1024,1,1,4,1,area,area));
    assert(!valid_output(2,768,1024,1,2,4,1,2*area,area));
    assert(!valid_output(3,768,1024,1,2,4,1,2*area-1,area));
    assert(!valid_output(3,768,1024,1,1,2,1,area,area));
    assert(!valid_output(3,768,1024,1,1,4,4,area,area));
    assert(!valid_output(3,0,1024,1,1,4,1,area,area));
    assert(!valid_output(3,INT_MAX,INT_MAX,1,2,4,1,INT_MAX,area));
    assert(!valid_output(3,1024,1024,1,1,4,1,1024*1024,area));
    assert(!valid_output(3,768,1024,1,0,4,1,area,area));

    // Both output roles must reject an extra depth axis or an unsupported rank,
    // even when their width/height/channel count fit the destination capacity.
    const int invalid_ranks[] = {4, 0, 1, -1, INT_MAX};
    const int invalid_depths[] = {2, 0, -1, INT_MAX};
    for (int channels = 1; channels <= 2; ++channels) {
        for (int rank : invalid_ranks) {
            assert(!valid_output(rank,768,1024,1,channels,4,1,channels*area,area));
        }
        for (int depth : invalid_depths) {
            assert(!valid_output(3,768,1024,depth,channels,4,1,channels*area,area));
            if (channels == 1) assert(!valid_output(2,768,1024,depth,1,4,1,area,area));
        }
        assert(!valid_output(4,768,1024,2,channels,4,1,channels*area,area));
    }
}
