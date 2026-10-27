// Replica de random.Random(semilla) de Python (Mersenne Twister MT19937).
//
// Sirve para que la red grande (CP-13) sea EXACTAMENTE la misma que genera el
// prototipo en Python con la misma semilla, y asi poder comparar resultados
// entre ambas implementaciones.
#pragma once

#include <cstdint>

namespace churoviaje {

class AleatorioPython {
public:
    explicit AleatorioPython(std::uint32_t semilla) { iniciar_por_arreglo(semilla); }

    // random.random(): flotante en [0, 1) con 53 bits.
    double random() {
        const std::uint32_t a = siguiente() >> 5;
        const std::uint32_t b = siguiente() >> 6;
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
    }

    // random.uniform(a, b)
    double uniform(double a, double b) { return a + (b - a) * random(); }

private:
    static constexpr int N = 624;
    static constexpr int M = 397;
    std::uint32_t mt_[N];
    int mti_ = N;

    void iniciar(std::uint32_t s) {
        mt_[0] = s;
        for (mti_ = 1; mti_ < N; ++mti_)
            mt_[mti_] = 1812433253u * (mt_[mti_ - 1] ^ (mt_[mti_ - 1] >> 30)) + static_cast<std::uint32_t>(mti_);
    }

    // Python siembra un int con init_by_array([semilla]).
    void iniciar_por_arreglo(std::uint32_t semilla) {
        const std::uint32_t clave[1] = {semilla};
        const int largo = 1;
        iniciar(19650218u);
        int i = 1, j = 0;
        for (int k = (N > largo ? N : largo); k; --k) {
            mt_[i] = (mt_[i] ^ ((mt_[i - 1] ^ (mt_[i - 1] >> 30)) * 1664525u)) + clave[j] + static_cast<std::uint32_t>(j);
            ++i; ++j;
            if (i >= N) { mt_[0] = mt_[N - 1]; i = 1; }
            if (j >= largo) j = 0;
        }
        for (int k = N - 1; k; --k) {
            mt_[i] = (mt_[i] ^ ((mt_[i - 1] ^ (mt_[i - 1] >> 30)) * 1566083941u)) - static_cast<std::uint32_t>(i);
            ++i;
            if (i >= N) { mt_[0] = mt_[N - 1]; i = 1; }
        }
        mt_[0] = 0x80000000u;
        mti_ = N;
    }

    std::uint32_t siguiente() {
        if (mti_ >= N) {
            int kk = 0;
            std::uint32_t y;
            for (; kk < N - M; ++kk) {
                y = (mt_[kk] & 0x80000000u) | (mt_[kk + 1] & 0x7fffffffu);
                mt_[kk] = mt_[kk + M] ^ (y >> 1) ^ ((y & 1u) ? 0x9908b0dfu : 0u);
            }
            for (; kk < N - 1; ++kk) {
                y = (mt_[kk] & 0x80000000u) | (mt_[kk + 1] & 0x7fffffffu);
                mt_[kk] = mt_[kk + (M - N)] ^ (y >> 1) ^ ((y & 1u) ? 0x9908b0dfu : 0u);
            }
            y = (mt_[N - 1] & 0x80000000u) | (mt_[0] & 0x7fffffffu);
            mt_[N - 1] = mt_[M - 1] ^ (y >> 1) ^ ((y & 1u) ? 0x9908b0dfu : 0u);
            mti_ = 0;
        }
        std::uint32_t y = mt_[mti_++];
        y ^= (y >> 11);
        y ^= (y << 7) & 0x9d2c5680u;
        y ^= (y << 15) & 0xefc60000u;
        y ^= (y >> 18);
        return y;
    }
};

}  // namespace churoviaje
