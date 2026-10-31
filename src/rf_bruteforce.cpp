#include "rf_bruteforce.h"
#include "cc1101_driver.h"
#include "rf_encoder.h"

RfBruteforce rfBruteforce;

RfBruteforce::RfBruteforce() {}

bool RfBruteforce::begin(const RfProtocolDef *def, int bits, float freqMhz, int repeat) {
    stop();
    if (def == nullptr) return false;

    m_def = def;
    m_bits = (bits > 0) ? bits : def->bits;
    if (m_bits <= 0 || m_bits > 32) return false; // keep 2^bits in range of uint32_t/uint64_t loop
    m_repeat = (repeat < 1) ? 1 : repeat;
    m_code = 0;
    m_total = (uint64_t)1 << m_bits;

    cc1101Driver.setFrequency(freqMhz, true);

    m_active = true;
    Serial.printf(
        "[BRUTEFORCE] Started proto=%s bits=%d codes=%llu repeat=%d freq=%.2f MHz\n",
        def->name,
        m_bits,
        (unsigned long long)m_total,
        m_repeat,
        freqMhz
    );
    return true;
}

void RfBruteforce::stop() {
    if (!m_active) return;
    cc1101Driver.setIdle();
    m_active = false;
    Serial.println("[BRUTEFORCE] Stopped.");
}

bool RfBruteforce::tick() {
    if (!m_active) return false;
    if (m_code >= m_total) {
        Serial.println("[BRUTEFORCE] Keyspace exhausted.");
        stop();
        return false;
    }

    rf_tx_protocol(m_code, (unsigned int)m_bits, m_def->te, m_def, m_repeat);

    if ((m_code % 100) == 0) {
        Serial.printf(
            "[BRUTEFORCE] %llu/%llu (%s)\n", (unsigned long long)m_code, (unsigned long long)m_total, m_def->name
        );
    }

    m_code++;
    return m_code < m_total;
}
