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

/// Statistics for the actual retained window, never inferred battery health.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct BatteryWindowStats {
    pub count: u16,
    pub min_temperature_deci_c: i16,
    pub max_temperature_deci_c: i16,
    pub mean_temperature_deci_c: i16,
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

    pub fn statistics(&self) -> Option<BatteryWindowStats> {
        let first = self.samples.front()?;
        let mut min = first.temperature_deci_c;
        let mut max = first.temperature_deci_c;
        let mut total: i64 = 0;
        for sample in &self.samples {
            min = min.min(sample.temperature_deci_c);
            max = max.max(sample.temperature_deci_c);
            total += i64::from(sample.temperature_deci_c);
        }
        let count = self.samples.len();
        Some(BatteryWindowStats {
            count: count as u16,
            min_temperature_deci_c: min as i16,
            max_temperature_deci_c: max as i16,
            mean_temperature_deci_c: (total / count as i64) as i16,
        })
    }

    /// Number of user-triggered samples discarded from process-only history.
    pub fn clear(&mut self) -> usize {
        let count = self.samples.len();
        self.samples.clear();
        count
    }

    /// Actual duration between the oldest and newest retained monotonic samples.
    /// No extrapolation or background measurement.
    pub fn duration_ms(&self) -> Option<i64> {
        let oldest = self.samples.front()?;
        let newest = self.samples.back()?;
        newest.timestamp_ms.checked_sub(oldest.timestamp_ms)
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

/// ABI: [count:u16 @48] [min temp:i16 @32] [max temp:i16 @16] [mean temp:i16].
/// Only actual recorded temperature values are included; -1 represents no samples.
pub fn encode_window_stats(stats: BatteryWindowStats) -> i64 {
    ((stats.count as i64) << 48)
        | ((stats.min_temperature_deci_c as u16 as i64) << 32)
        | ((stats.max_temperature_deci_c as u16 as i64) << 16)
        | (stats.mean_temperature_deci_c as u16 as i64)
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

/// Read-only JNI query: returns -1 if the session has no samples or lock is poisoned.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_lolokeksu_legioncontrol_RustEngine_readBatteryWindowStats(
    _env: *mut c_void,
    _receiver: *mut c_void,
) -> i64 {
    let Some(history) = HISTORY.get() else { return INVALID_NATIVE_RESULT };
    let Ok(guard) = history.lock() else { return INVALID_NATIVE_RESULT };
    guard.statistics().map(encode_window_stats).unwrap_or(INVALID_NATIVE_RESULT)
}

/// Explicit user-requested reset; only local, in-memory samples are cleared.
/// A negative return means the lock was unavailable.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_lolokeksu_legioncontrol_RustEngine_resetBatterySession(
    _env: *mut c_void,
    _receiver: *mut c_void,
) -> i32 {
    let history = HISTORY.get_or_init(|| Mutex::new(BatteryHistory::default()));
    let Ok(mut guard) = history.lock() else { return -1 };
    guard.clear() as i32
}

/// Read only: returns -1 when there are no recorded samples or lock is unavailable.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_lolokeksu_legioncontrol_RustEngine_readBatterySessionDuration(
    _env: *mut c_void,
    _receiver: *mut c_void,
) -> i64 {
    let Some(history) = HISTORY.get() else { return INVALID_NATIVE_RESULT };
    let Ok(guard) = history.lock() else { return INVALID_NATIVE_RESULT };
    guard.duration_ms().unwrap_or(INVALID_NATIVE_RESULT)
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
    fn empty_history_has_no_statistics() {
        assert_eq!(BatteryHistory::default().statistics(), None);
    }

    #[test]
    fn window_statistics_are_exact_and_read_only() {
        let mut history = BatteryHistory::default();
        history.record(sample(60, -100, 1)).unwrap();
        history.record(sample(60, 200, 2)).unwrap();
        history.record(sample(59, 300, 3)).unwrap();
        let before = history.samples.clone();
        let metrics = history.statistics().unwrap();
        assert_eq!(metrics, BatteryWindowStats {
            count: 3, min_temperature_deci_c: -100,
            max_temperature_deci_c: 300, mean_temperature_deci_c: 133
        });
        assert_eq!(history.samples, before);
        let encoded = encode_window_stats(metrics);
        assert_eq!(((encoded >> 48) & 0xffff) as u16, 3);
        assert_eq!(((encoded >> 32) & 0xffff) as u16 as i16, -100);
        assert_eq!(((encoded >> 16) & 0xffff) as u16 as i16, 300);
        assert_eq!((encoded & 0xffff) as u16 as i16, 133);
    }

    #[test]
    fn window_uses_only_the_last_64_readings() {
        let mut history = BatteryHistory::default();
        history.record(sample(50, -100, 1)).unwrap();
        for time in 2..=65 {
            history.record(sample(50, 250, time)).unwrap();
        }
        let metrics = history.statistics().unwrap();
        assert_eq!(metrics.count, 64);
        assert_eq!(metrics.min_temperature_deci_c, 250);
        assert_eq!(metrics.max_temperature_deci_c, 250);
        assert_eq!(metrics.mean_temperature_deci_c, 250);
    }

    #[test]
    fn duration_is_based_on_actual_samples_and_reset_removes_them() {
        let mut history = BatteryHistory::default();
        assert_eq!(history.duration_ms(), None);
        assert_eq!(history.clear(), 0);
        history.record(sample(75, 300, 10_000)).unwrap();
        assert_eq!(history.duration_ms(), Some(0));
        history.record(sample(74, 315, 12_500)).unwrap();
        assert_eq!(history.duration_ms(), Some(2500));
        assert_eq!(history.clear(), 2);
        assert_eq!(history.duration_ms(), None);
        assert_eq!(history.statistics(), None);
        // After an explicit reset, a new monotonic session can start independently.
        history.record(sample(60, 270, 1_000)).unwrap();
        assert_eq!(history.duration_ms(), Some(0));
        assert_eq!(history.statistics().unwrap().count, 1);
    }

    #[test]
    fn duration_uses_only_retained_window_when_older_samples_roll_out() {
        let mut history = BatteryHistory::default();
        for seconds in 1..=70 {
            history.record(sample(50, 250, seconds * 1_000)).unwrap();
        }
        assert_eq!(history.statistics().unwrap().count, 64);
        assert_eq!(history.duration_ms(), Some(63_000));
        assert_eq!(history.clear(), 64);
        assert_eq!(history.clear(), 0);
    }

    #[test]
    fn jni_returns_error_for_invalid_reading() {
        let value = Java_dev_lolokeksu_legioncontrol_RustEngine_recordBatterySample(
            std::ptr::null_mut(), std::ptr::null_mut(), 150, 240, 1);
        assert_eq!(value, INVALID_NATIVE_RESULT);
    }
}
