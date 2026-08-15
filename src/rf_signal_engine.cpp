#include "rf_signal_engine.h"

RFSignalEngine rfSignalEngine;

static void pushHalfSymbol(std::vector<rmt_symbol_word_t> &syms, bool &pendingLow, uint8_t level, long dur) {
    while (dur > 0) {
        uint16_t chunk = (dur > RMT_MAX_PULSE_DUR) ? RMT_MAX_PULSE_DUR : (uint16_t)dur;
        dur -= chunk;
        if (!pendingLow) {
            rmt_symbol_word_t s = {};
            s.level0 = level;
            s.duration0 = chunk;
            syms.push_back(s);
            pendingLow = true;
        } else {
            rmt_symbol_word_t &s = syms.back();
            s.level1 = level;
            s.duration1 = chunk;
            pendingLow = false;
        }
    }
}

size_t durationsToRmtSymbols(const std::vector<int> &durations, std::vector<rmt_symbol_word_t> &syms) {
    syms.clear();
    if (durations.empty()) return 0;
    syms.reserve(durations.size() / 2 + 1);
    bool pendingLow = false;
    for (int d : durations) {
        if (d == 0) continue;
        uint8_t level = (d > 0) ? 1 : 0;
        long dur = (d > 0) ? d : -d;
        pushHalfSymbol(syms, pendingLow, level, dur);
    }
    return syms.size();
}

void rmtSymbolsToDurations(const rmt_symbol_word_t *symbols, size_t count, std::vector<int> &out) {
    out.clear();
    if (!symbols || count == 0) return;
    out.reserve(count * 2);

    int pendingHigh = 0;
    int pendingLow = 0;

    auto pushHalf = [&](uint8_t level, int dur) {
        if (dur <= 0) return;
        if (level) {
            if (pendingLow < 0) {
                out.push_back(pendingLow);
                pendingLow = 0;
            }
            pendingHigh += dur;
        } else {
            if (pendingHigh > 0) {
                out.push_back(pendingHigh);
                pendingHigh = 0;
            }
            pendingLow -= dur;
        }
    };

    for (size_t i = 0; i < count; i++) {
        pushHalf(symbols[i].level0, symbols[i].duration0);
        pushHalf(symbols[i].level1, symbols[i].duration1);
    }

    if (pendingHigh > 0) out.push_back(pendingHigh);
    if (pendingLow < 0) out.push_back(pendingLow);
}

RFSignalEngine::RFSignalEngine() {}

RFSignalEngine::~RFSignalEngine() { stopRx(); }

bool RFSignalEngine::rxDoneCallback(
    rmt_channel_handle_t channel, const rmt_rx_done_event_data_t *edata, void *user_data
) {
    BaseType_t high_task_wakeup = pdFALSE;
    QueueHandle_t queue = (QueueHandle_t)user_data;
    if (queue) { xQueueSendFromISR(queue, edata, &high_task_wakeup); }
    return high_task_wakeup == pdTRUE;
}

bool RFSignalEngine::startRx(float frequency, size_t bufSymbols) {
    if (m_rxActive) stopRx();

    Serial.printf("[RMT Engine] Starting RX Session on GPIO %d at %.2f MHz...\n", CC1101_GDO0_PIN, frequency);

    if (!cc1101Driver.setRxMode(frequency)) {
        Serial.println("[RMT Engine] Failed to configure CC1101 RX mode!");
        return false;
    }

    m_bufSymbols = bufSymbols;
    m_rxBuf = (rmt_symbol_word_t *)malloc(m_bufSymbols * sizeof(rmt_symbol_word_t));
    if (!m_rxBuf) {
        Serial.println("[RMT Engine] ERROR: Failed to allocate RMT symbol buffer!");
        return false;
    }

    rmt_rx_channel_config_t rx_cfg = {};
    rx_cfg.gpio_num = (gpio_num_t)CC1101_GDO0_PIN;
    rx_cfg.clk_src = RMT_CLK_SRC_DEFAULT;
    rx_cfg.resolution_hz = 1 * 1000 * 1000; // 1 MHz resolution (1 tick = 1 µs)
    rx_cfg.mem_block_symbols = 64;
    rx_cfg.flags.invert_in = false;
    rx_cfg.flags.with_dma = false;

    esp_err_t err = rmt_new_rx_channel(&rx_cfg, &m_rxChannel);
    if (err != ESP_OK) {
        Serial.printf("[RMT Engine] rmt_new_rx_channel failed: %d\n", (int)err);
        free(m_rxBuf);
        m_rxBuf = nullptr;
        return false;
    }

    m_rxQueue = xQueueCreate(1, sizeof(rmt_rx_done_event_data_t));
    if (!m_rxQueue) {
        rmt_del_channel(m_rxChannel);
        m_rxChannel = nullptr;
        free(m_rxBuf);
        m_rxBuf = nullptr;
        return false;
    }

    rmt_rx_event_callbacks_t cbs = {};
    cbs.on_recv_done = rxDoneCallback;
    if (rmt_rx_register_event_callbacks(m_rxChannel, &cbs, m_rxQueue) != ESP_OK) {
        Serial.println("[RMT Engine] rmt_rx_register_event_callbacks failed!");
        stopRx();
        return false;
    }

    if (rmt_enable(m_rxChannel) != ESP_OK) {
        Serial.println("[RMT Engine] rmt_enable failed!");
        stopRx();
        return false;
    }

    rmt_receive_config_t rmt_cfg = {};
    rmt_cfg.signal_range_min_ns = 3000;     // 3 µs minimum filter
    rmt_cfg.signal_range_max_ns = 30000000; // 30 ms idle timeout

    err = rmt_receive(m_rxChannel, m_rxBuf, m_bufSymbols * sizeof(rmt_symbol_word_t), &rmt_cfg);
    if (err != ESP_OK) {
        Serial.printf("[RMT Engine] rmt_receive failed: %d\n", (int)err);
        stopRx();
        return false;
    }

    m_rxActive = true;
    Serial.println("[RMT Engine] RX Session ACTIVE.");
    Serial.flush();
    return true;
}

void RFSignalEngine::stopRx() {
    if (!m_rxActive && !m_rxChannel) return;

    if (m_rxChannel) {
        rmt_disable(m_rxChannel);
        rmt_del_channel(m_rxChannel);
        m_rxChannel = nullptr;
    }
    if (m_rxQueue) {
        vQueueDelete(m_rxQueue);
        m_rxQueue = nullptr;
    }
    if (m_rxBuf) {
        free(m_rxBuf);
        m_rxBuf = nullptr;
    }
    m_rxActive = false;
    cc1101Driver.setIdle();
    Serial.println("[RMT Engine] RX Session STOPPED.");
    Serial.flush();
}

bool RFSignalEngine::pollRxDurations(std::vector<int> &outDurations, uint32_t timeoutMs) {
    if (!m_rxActive || !m_rxQueue) return false;

    rmt_rx_done_event_data_t rxData;
    TickType_t ticks = (timeoutMs == 0) ? 0 : pdMS_TO_TICKS(timeoutMs);

    if (xQueueReceive(m_rxQueue, &rxData, ticks) == pdTRUE) {
        rmtSymbolsToDurations(rxData.received_symbols, rxData.num_symbols, outDurations);

        // Re-arm for continuous background capture
        rmt_receive_config_t rmt_cfg = {};
        rmt_cfg.signal_range_min_ns = 3000;
        rmt_cfg.signal_range_max_ns = 30000000;
        rmt_receive(m_rxChannel, m_rxBuf, m_bufSymbols * sizeof(rmt_symbol_word_t), &rmt_cfg);

        return !outDurations.empty();
    }
    return false;
}

bool RFSignalEngine::transmitDurations(const std::vector<int> &durations, float frequency) {
    if (durations.empty()) return false;

    bool wasRx = m_rxActive;
    if (wasRx) stopRx();

    Serial.printf(
        "[RMT Engine] Transmitting %u durations on GPIO %d at %.2f MHz...\n",
        (unsigned int)durations.size(),
        CC1101_GDO0_PIN,
        frequency
    );

    if (!cc1101Driver.setTxMode(frequency)) {
        Serial.println("[RMT Engine] Failed to configure CC1101 TX mode!");
        return false;
    }

    std::vector<rmt_symbol_word_t> syms;
    if (durationsToRmtSymbols(durations, syms) == 0) {
        Serial.println("[RMT Engine] ERROR: Symbol conversion failed!");
        cc1101Driver.setIdle();
        return false;
    }

    rmt_tx_channel_config_t tx_cfg = {};
    tx_cfg.gpio_num = (gpio_num_t)CC1101_GDO0_PIN;
    tx_cfg.clk_src = RMT_CLK_SRC_DEFAULT;
    tx_cfg.resolution_hz = 1 * 1000 * 1000; // 1 MHz resolution (1 tick = 1 µs)
    tx_cfg.mem_block_symbols = 64;
    tx_cfg.trans_queue_depth = 4;
    tx_cfg.flags.invert_out = false;
    tx_cfg.flags.with_dma = false;

    rmt_channel_handle_t ch = nullptr;
    esp_err_t err = rmt_new_tx_channel(&tx_cfg, &ch);
    if (err != ESP_OK) {
        Serial.printf("[RMT Engine] rmt_new_tx_channel failed: %d\n", (int)err);
        cc1101Driver.setIdle();
        return false;
    }

    rmt_encoder_handle_t encoder = nullptr;
    rmt_copy_encoder_config_t copy_cfg = {};
    err = rmt_new_copy_encoder(&copy_cfg, &encoder);
    if (err != ESP_OK) {
        Serial.printf("[RMT Engine] rmt_new_copy_encoder failed: %d\n", (int)err);
        rmt_del_channel(ch);
        cc1101Driver.setIdle();
        return false;
    }

    bool ok = (rmt_enable(ch) == ESP_OK);
    if (ok) {
        rmt_transmit_config_t txc = {};
        txc.loop_count = 0;
        txc.flags.eot_level = 0;

        err = rmt_transmit(ch, encoder, syms.data(), syms.size() * sizeof(rmt_symbol_word_t), &txc);
        if (err != ESP_OK) {
            Serial.printf("[RMT Engine] rmt_transmit failed: %d\n", (int)err);
            ok = false;
        } else {
            err = rmt_tx_wait_all_done(ch, 2000);
            if (err != ESP_OK) {
                Serial.printf("[RMT Engine] rmt_tx_wait_all_done failed: %d\n", (int)err);
                ok = false;
            }
        }
        rmt_disable(ch);
    }

    rmt_del_encoder(encoder);
    rmt_del_channel(ch);

    cc1101Driver.setIdle();
    Serial.printf("[RMT Engine] Transmission complete (%s).\n", ok ? "SUCCESS" : "FAILED");
    Serial.flush();

    if (wasRx) startRx(frequency);
    return ok;
}
