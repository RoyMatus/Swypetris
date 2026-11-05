-- Run with trace_processor_shell query --full-sort -f tools/energy-metrics.sql <trace>.
-- CPU includes the identical instrumentation driver. Scheduling slices are not wakeup counts.
CREATE PERFETTO TABLE energy_window AS
SELECT s.ts, s.dur, pt.upid FROM slice s JOIN process_track pt ON s.track_id = pt.id
WHERE s.name IN ('SwypetrisEnergyGameplay', 'SwypetrisEnergyMenu') AND s.dur > 0;

SELECT COUNT(*) AS workload_markers, ROUND(SUM(dur) / 1e9, 3) AS workload_seconds FROM energy_window;

CREATE PERFETTO TABLE energy_sched AS
SELECT p.upid, p.name AS process_name, t.utid, t.name AS thread_name, t.is_main_thread,
       MAX(0, MIN(s.ts + s.dur, w.ts + w.dur) - MAX(s.ts, w.ts)) AS measured_dur
FROM sched s JOIN thread t USING (utid) JOIN process p USING (upid) CROSS JOIN energy_window w
WHERE p.upid = w.upid AND s.dur > 0 AND s.ts < w.ts + w.dur AND s.ts + s.dur > w.ts;

SELECT process_name, COUNT(*) AS scheduled_slices,
       ROUND(SUM(measured_dur) / 1e9, 3) AS cpu_seconds,
       ROUND(100.0 * SUM(measured_dur) / (SELECT SUM(dur) FROM energy_window), 3) AS percent_of_one_cpu
FROM energy_sched GROUP BY upid;

SELECT thread_name, COUNT(*) AS scheduled_slices, ROUND(SUM(measured_dur) / 1e9, 3) AS cpu_seconds
FROM energy_sched WHERE is_main_thread GROUP BY utid;

-- These counters cover the entire device, including unrelated apps and system services.
SELECT ct.name, ROUND((MAX(c.value) - MIN(c.value)) / 1e6, 3) AS energy_joules,
       ROUND((MAX(c.value) - MIN(c.value)) * 1e6 / (MAX(c.ts) - MIN(c.ts)), 3) AS average_mw
FROM counter c JOIN counter_track ct ON c.track_id = ct.id CROSS JOIN energy_window w
WHERE ct.name GLOB 'power.rails.*' AND c.ts BETWEEN w.ts AND w.ts + w.dur
GROUP BY ct.id HAVING COUNT(*) > 1 ORDER BY ct.name;

-- Charge counters are microampere-hours. Use the sampled interval, not the later file transfer.
WITH samples AS (
    SELECT c.ts, c.value,
           ROW_NUMBER() OVER (ORDER BY c.ts) AS first_row,
           ROW_NUMBER() OVER (ORDER BY c.ts DESC) AS last_row
    FROM counter c JOIN counter_track ct ON c.track_id = ct.id CROSS JOIN energy_window w
    WHERE ct.name = 'batt.charge_uah' AND c.ts BETWEEN w.ts AND w.ts + w.dur
), endpoints AS (
    SELECT COUNT(*) AS charge_samples, (MAX(ts) - MIN(ts)) / 1e9 AS sample_seconds,
           MAX(CASE WHEN first_row = 1 THEN value END) AS first_charge_uah,
           MAX(CASE WHEN last_row = 1 THEN value END) AS last_charge_uah
    FROM samples
)
SELECT charge_samples, ROUND(sample_seconds, 3) AS sample_seconds,
       first_charge_uah, last_charge_uah,
       ROUND((first_charge_uah - last_charge_uah) / 1000.0, 3) AS device_charge_used_mah,
       ROUND((first_charge_uah - last_charge_uah) / 1000.0 * 3600 / sample_seconds, 3) AS device_mah_per_hour
FROM endpoints;

-- App doFrame CPU-side durations, not complete presentation latency or GPU duration.
SELECT COUNT(*) AS frame_callbacks, ROUND(AVG(s.dur) / 1e6, 3) AS mean_do_frame_ms,
       ROUND(PERCENTILE(s.dur, 95) / 1e6, 3) AS p95_do_frame_ms,
       ROUND(MAX(s.dur) / 1e6, 3) AS max_do_frame_ms
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid) JOIN process p USING (upid) CROSS JOIN energy_window w
WHERE p.upid = w.upid AND s.name GLOB 'Choreographer#doFrame*' AND s.dur > 0
      AND s.ts BETWEEN w.ts AND w.ts + w.dur;

SELECT COUNT(*) AS presented_frames,
       SUM(CASE WHEN a.jank_type GLOB '*App Deadline Missed*' THEN 1 ELSE 0 END) AS app_deadline_misses,
       ROUND(PERCENTILE(a.dur, 95) / 1e6, 3) AS p95_presentation_ms
FROM actual_frame_timeline_slice a JOIN process p USING (upid) CROSS JOIN energy_window w
WHERE p.upid = w.upid AND a.surface_frame_token != 0 AND a.dur > 0
      AND a.ts BETWEEN w.ts AND w.ts + w.dur;

SELECT name, value FROM stats WHERE severity IN ('error', 'data_loss') AND value > 0;
