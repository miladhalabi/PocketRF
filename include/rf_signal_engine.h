#ifndef RF_SIGNAL_ENGINE_H
#define RF_SIGNAL_ENGINE_H

#include "app_config.h"
#include "cc1101_driver.h"
#include <Arduino.h>
#include <driver/rmt_rx.h>
#include <driver/rmt_tx.h>
#include <vector>

#define RMT_MAX_PULSE_DUR 32767

// Convert signed microsecond durations to RMT symbol words
size_t durationsToRmtSymbols(const std::vector<int> &durations, std::vector<rmt_symbol_word_t> &syms);

// Convert RMT symbol words to signed microsecond durations
void rmtSymbolsToDurations(const rmt_symbol_word_t *symbols, size_t count, std::vector<int> &outDurations);

class RFSignalEngine {
public:
    RFSignalEngine();
    ~RFSignalEngine();

    // RMT RX Session
    bool startRx(float frequency = DEFAULT_RF_FREQ, size_t bufSymbols = 1000);
    void stopRx();
    bool isRxActive() const { return m_rxActive; }
    bool pollRxDurations(std::vector<int> &outDurations, uint32_t timeoutMs = 0);

    // RMT TX Session
    bool transmitDurations(const std::vector<int> &durations, float frequency = DEFAULT_RF_FREQ);

private:
    bool m_rxActive = false;
    rmt_channel_handle_t m_rxChannel = nullptr;
    QueueHandle_t m_rxQueue = nullptr;
    rmt_symbol_word_t *m_rxBuf = nullptr;
    size_t m_bufSymbols = 1000;

    static bool
    rxDoneCallback(rmt_channel_handle_t channel, const rmt_rx_done_event_data_t *edata, void *user_data);
};

extern RFSignalEngine rfSignalEngine;

#endif // RF_SIGNAL_ENGINE_H
