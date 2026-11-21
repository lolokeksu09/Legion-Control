//! Read-only battery sampling backend. No hardware writes, network access, or OEM APIs.
use std::collections::VecDeque;
use std::ffi::c_void;
use std::sync::{Mutex, OnceLock};

const HISTORY_LIMIT: usize = 64;
const INVALID_NATIVE_RESULT: i64 = -1;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct BatterySample {
    pub level_percent: i32,
    pub temperature_deci_c: i32,
    pub timestamp_ms: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct BatteryTrend {
    pub count: u16,
    pub level_delta_percent: i16,
    pub temperature_delta_deci_c: i16,
}

#[derive(Default)]
pub struct BatteryHistory {
    samples: VecDeque<BatterySample>,
}

impl BatteryHistory {
    pub fn record(&mut self, sample: BatterySample) -> Result<BatteryTrend, &'static str> {
        if !(0..=100).contains(&sample.level_percent) {
            return Err("battery percentage outside Android contract");
        }
        // Broad sanity check for corrupted input, NOT an OEM thermal threshold.
        if !(-400..=1200).contains(&sample.temperature_deci_c) {
            return Err("temperature sensor value out of range");
        }
        if sample.timestamp_ms <= 0 {
            return Err("invalid timestamp");
        }
        if let Some(last) = self.samples.back() {
            if sample.timestamp_ms < last.timestamp_ms {
                return Err("out of order timestamp");
            }
            if sample.timestamp_ms == last.timestamp_ms {
                if sample == *last { return Ok(self.trend()); }
                return Err("conflicting sample at same timestamp");
            }
        }
        if self.samples.len() == HISTORY_LIMIT { self.samples.pop_front(); }
        self.samples.push_back(sample);
        Ok(self.trend())
    }

    fn trend(&self) -> BatteryTrend {
        let first = self.samples.front().expect("trend requires a sample");
        let last = self.samples.back().expect("trend requires a sample");
        BatteryTrend {
            count: self.samples.len() as u16,
            level_delta_percent: (last.level_percent - first.level_percent) as i16,
            temperature_delta_deci_c:
                (last.temperature_deci_c - first.temperature_deci_c) as i16,
        }
    }
}

/// Packed stable ABI: [count:16 @ 32] [temperature delta signed:16 @ 16]
/// [battery percent delta signed:16 @ 0]. Negative one means invalid input.
pub fn encode_trend(trend: BatteryTrend) -> i64 {
    ((trend.count as i64) << 32)
        | (((trend.temperature_delta_deci_c as u16) as i64) << 16)
        | (trend.level_delta_percent as u16) as i64
}

static HISTORY: OnceLock<Mutex<BatteryHistory>> = OnceLock::new();

/// JNI bridge for Kotlin instance method RustEngine.recordBatterySample(...).
/// The JNI handles are never dereferenced: only primitive parameters are used.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_lolokeksu_legioncontrol_RustEngine_recordBatterySample(
    _env: *mut c_void,
    _receiver: *mut c_void,
    level_percent: i32,
    temperature_deci_c: i32,
    timestamp_ms: i64,
) -> i64 {
    let history = HISTORY.get_or_init(|| Mutex::new(BatteryHistory::default()));
    let Ok(mut guard) = history.lock() else { return INVALID_NATIVE_RESULT };
    guard.record(BatterySample {
        level_percent,
        temperature_deci_c,
        timestamp_ms,
    }).map(encode_trend).unwrap_or(INVALID_NATIVE_RESULT)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn sample(level: i32, temp: i32, time: i64) -> BatterySample {
        BatterySample { level_percent: level, temperature_deci_c: temp, timestamp_ms: time }
    }

    #[test]
    fn real_trend_is_relative_to_first_sample() {
        let mut history = BatteryHistory::default();
        assert_eq!(history.record(sample(63, 315, 1000)).unwrap(), BatteryTrend {
            count: 1, level_delta_percent: 0, temperature_delta_deci_c: 0
        });
        assert_eq!(history.record(sample(62, 328, 3000)).unwrap(), BatteryTrend {
            count: 2, level_delta_percent: -1, temperature_delta_deci_c: 13
        });
    }

    #[test]
    fn rejects_invalid_and_out_of_order_inputs_without_mutation() {
        let mut history = BatteryHistory::default();
        history.record(sample(50, 250, 1000)).unwrap();
        assert!(history.record(sample(101, 250, 2000)).is_err());
        assert!(history.record(sample(51, 2000, 2000)).is_err());
        assert!(history.record(sample(49, 250, 999)).is_err());
        assert!(history.record(sample(49, 250, 1000)).is_err());
        assert_eq!(history.record(sample(50, 250, 1000)).unwrap().count, 1);
        assert_eq!(history.record(sample(50, 250, 2000)).unwrap().count, 2);
    }

    #[test]
    fn history_is_bounded_to_sixty_four_samples() {
        let mut history = BatteryHistory::default();
        for time in 1..=70 {
            history.record(sample(time, 250, time as i64)).unwrap();
        }
        let state = history.trend();
        assert_eq!(state.count, 64);
        assert_eq!(state.level_delta_percent, 63);
    }

    #[test]
    fn encoding_preserves_negative_deltas() {
        let encoded = encode_trend(BatteryTrend {
            count: 2, level_delta_percent: -2, temperature_delta_deci_c: -15,
        });
        assert_eq!((encoded & 0xffff) as u16 as i16, -2);
        assert_eq!(((encoded >> 16) & 0xffff) as u16 as i16, -15);
        assert_eq!(((encoded >> 32) & 0xffff) as u16, 2);
    }

    #[test]
    fn jni_returns_error_for_invalid_reading() {
        let value = Java_dev_lolokeksu_legioncontrol_RustEngine_recordBatterySample(
            std::ptr::null_mut(), std::ptr::null_mut(), 150, 240, 1);
        assert_eq!(value, INVALID_NATIVE_RESULT);
    }
}
