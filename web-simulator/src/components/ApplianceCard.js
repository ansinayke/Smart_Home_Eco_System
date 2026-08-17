"use client";

import { useState, useEffect } from "react";
import { statusStyle, typeLabel } from "@/lib/devices";

function SwitchRow({ name, status, onToggle }) {
  const style = statusStyle(status);
  const on = status === "ON";

  return (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <span className="text-sm text-slate-300">{name}</span>
      <button
        onClick={onToggle}
        className={`inline-flex h-6 w-11 items-center rounded-sm px-1 transition-colors cursor-pointer outline-none focus:ring-2 focus:ring-emerald-500/50 ${
          on ? "bg-emerald-500/80" : "bg-slate-700"
        }`}
        aria-label={`${name} ${status}`}
      >
        <span
          className={`h-4 w-4 rounded-sm bg-white transition-transform ${
            on ? "translate-x-5" : "translate-x-0"
          }`}
        />
      </button>
      <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold tracking-wide ${style.badge}`}>
        {style.label}
      </span>
    </div>
  );
}

function CameraPanel({ device }) {
  const style = statusStyle(device.status);
  const live = device.status === "ON";
  const [openedTime, setOpenedTime] = useState(null);

  useEffect(() => {
    if (live) {
      setOpenedTime(Date.now());
    }
  }, [live]);

  useEffect(() => {
    if (live && device.mock_stream_uri) {
      const video = document.getElementById(`video-${device.id}`);
      if (video) {
        if (video.canPlayType('application/vnd.apple.mpegurl')) {
          video.src = device.mock_stream_uri;
        } else if (window.Hls && window.Hls.isSupported()) {
          const hls = new window.Hls();
          hls.loadSource(device.mock_stream_uri);
          hls.attachMedia(video);
        } else {
           // Fallback load script if Hls not found yet
           const script = document.createElement('script');
           script.src = "https://cdn.jsdelivr.net/npm/hls.js@1";
           script.async = true;
           script.onload = () => {
             if (window.Hls.isSupported()) {
               const hls = new window.Hls();
               hls.loadSource(device.mock_stream_uri);
               hls.attachMedia(video);
             }
           };
           document.body.appendChild(script);
        }
      }
    }
  }, [live, device.mock_stream_uri, device.id]);

  return (
    <div className="mt-3 overflow-hidden rounded-sm border border-slate-700 bg-slate-950">
      <div
        className={`relative flex aspect-video items-center justify-center ${
          live
            ? "bg-[radial-gradient(circle_at_30%_20%,#1e3a5f,transparent_50%),linear-gradient(160deg,#0f172a,#020617)]"
            : "bg-slate-900"
        }`}
      >
        {live ? (
          <>
            <video
              id={`video-${device.id}`}
              className="absolute inset-0 h-full w-full object-cover pointer-events-none"
              autoPlay
              muted
              playsInline
              loop
            />
            <div className="absolute left-2 top-2 flex items-center gap-1.5 z-10 bg-black/40 px-2 py-0.5 rounded backdrop-blur-sm">
              <span className="h-2 w-2 animate-pulse rounded-full bg-rose-500" />
              <span className="text-[10px] font-semibold tracking-widest text-rose-300 drop-shadow-md">
                LIVE
              </span>
            </div>
          </>
        ) : (
          <p className="text-sm text-slate-500">{style.label}</p>
        )}
      </div>
      {(openedTime || device.last_snapshot_timestamp) ? (
        <p className="border-t border-slate-800 px-2 py-1 text-[10px] text-slate-500">
          Last snapshot:{" "}
          {new Date(openedTime || device.last_snapshot_timestamp).toLocaleString()}
        </p>
      ) : null}
    </div>
  );
}

function LightingExtras({ device }) {
  if (!device.is_scheduled || !device.schedule) return null;
  return (
    <p className="mt-2 text-xs text-amber-200/80">
      Schedule {device.schedule.turn_on_time} → {device.schedule.turn_off_time}
    </p>
  );
}

function SafetyExtras({ device }) {
  if (device.type !== "SMART_SWITCH" && device.type !== "SAFETY_HAZARD") return null;
  const max = device.max_on_duration_seconds;
  let elapsed = null;
  if (device.status === "ON" && device.last_turned_on_timestamp) {
    elapsed = Math.floor((Date.now() - device.last_turned_on_timestamp) / 1000);
  }
  return (
    <div className="mt-2 space-y-0.5 text-xs text-orange-200/90">
      <p>Auto-cutoff: {device.auto_cutoff_enabled ? (max != null ? `${max}s` : "—") : "Disabled"}</p>
      {elapsed != null ? <p>Active: {elapsed}s</p> : null}
    </div>
  );
}

export default function ApplianceCard({ device, onToggle, onSwitchToggle, onDelete, onSaveSchedule, onSaveCutoff }) {
  const style = statusStyle(device.status);
  const isOn = device.status === "ON";
  const [isEditing, setIsEditing] = useState(false);

  // Settings states
  const [enableSchedule, setEnableSchedule] = useState(device.is_scheduled || false);
  const [turnOn, setTurnOn] = useState(device.schedule?.turn_on_time || "18:00");
  const [turnOff, setTurnOff] = useState(device.schedule?.turn_off_time || "22:00");

  const [enableCutoff, setEnableCutoff] = useState(device.auto_cutoff_enabled || false);
  const [maxDuration, setMaxDuration] = useState(device.max_on_duration_seconds || 1800);

  const handleSave = () => {
    if (device.type === "LIGHTING" && onSaveSchedule) {
      onSaveSchedule(enableSchedule, turnOn, turnOff);
    }
    if (device.type === "SMART_SWITCH" && onSaveCutoff) {
      onSaveCutoff(enableCutoff, parseInt(maxDuration, 10));
    }
    setIsEditing(false);
  };

  return (
    <div className="group relative overflow-hidden rounded-xl border border-slate-800 bg-slate-900/50 p-5 backdrop-blur-sm transition-all hover:border-slate-700 hover:shadow-xl">
      <div className="flex items-start justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold tracking-wider text-slate-500 uppercase">
              {typeLabel(device.type)}
            </span>
            {(device.type === "LIGHTING" || device.type === "SMART_SWITCH") && (
                <button
                    onClick={() => setIsEditing(!isEditing)}
                    className="text-slate-400/50 hover:text-cyan-400 transition ml-2 opacity-0 group-hover:opacity-100"
                    title="Settings"
                >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                </button>
            )}
            {onDelete && (
                <button 
                    onClick={onDelete} 
                    className="text-rose-500/50 hover:text-rose-400 transition ml-2 opacity-0 group-hover:opacity-100" 
                    title="Delete Device"
                >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                </button>
            )}
          </div>
          <h3 className="mt-1 text-base font-semibold text-slate-50">
            {device.name}
          </h3>
          <p className="mt-0.5 font-mono text-[11px] text-slate-500">
            {device.id} · {device.floorName}
          </p>
        </div>
        <span
          className={`rounded px-2 py-1 text-[11px] font-bold tracking-wide ${style.badge}`}
        >
          {style.label}
        </span>
      </div>

      {device.type === "LIGHTING" && !isEditing ? <LightingExtras device={device} /> : null}
      {device.type === "SMART_SWITCH" && !isEditing ? <SafetyExtras device={device} /> : null}

      {isEditing && (
        <div className="mt-3 border-t border-slate-800 pt-3 flex flex-col gap-3">
            {device.type === "LIGHTING" && (
                <>
                    <label className="flex items-center gap-2 text-xs text-slate-300">
                        <input type="checkbox" checked={enableSchedule} onChange={(e) => setEnableSchedule(e.target.checked)} className="rounded bg-slate-800 border-slate-700" />
                        Enable Schedule
                    </label>
                    {enableSchedule && (
                        <div className="flex gap-2">
                            <input type="time" value={turnOn} onChange={(e) => setTurnOn(e.target.value)} className="w-full bg-slate-800 border border-slate-700 rounded p-1 text-xs text-white" />
                            <input type="time" value={turnOff} onChange={(e) => setTurnOff(e.target.value)} className="w-full bg-slate-800 border border-slate-700 rounded p-1 text-xs text-white" />
                        </div>
                    )}
                </>
            )}
            {device.type === "SMART_SWITCH" && (
                <>
                    <label className="flex items-center gap-2 text-xs text-slate-300">
                        <input type="checkbox" checked={enableCutoff} onChange={(e) => setEnableCutoff(e.target.checked)} className="rounded bg-slate-800 border-slate-700" />
                        Enable Auto-Cutoff
                    </label>
                    {enableCutoff && (
                        <input type="number" value={maxDuration} onChange={(e) => setMaxDuration(e.target.value)} className="w-full bg-slate-800 border border-slate-700 rounded p-1 text-xs text-white" placeholder="Max Duration (s)" />
                    )}
                </>
            )}
            <div className="flex justify-end gap-2 mt-1">
                <button onClick={() => setIsEditing(false)} className="px-2 py-1 text-[10px] bg-slate-800 text-slate-300 rounded hover:bg-slate-700 transition">Cancel</button>
                <button onClick={handleSave} className="px-2 py-1 text-[10px] bg-cyan-600 text-white rounded hover:bg-cyan-500 transition">Save</button>
            </div>
        </div>
      )}

      {device.type === "MULTI_SWITCH" && device.switches ? (
        <div className="mt-4 divide-y divide-slate-800 border-t border-slate-800 pt-3">
          {Object.entries(device.switches).map(([swId, sw]) => (
            <SwitchRow 
                key={swId} 
                name={sw.name || swId} 
                status={sw.status} 
                onToggle={() => onSwitchToggle && onSwitchToggle(swId, sw.status === "ON")} 
            />
          ))}
        </div>
      ) : null}

      {device.type === "SECURITY_CAMERA" ? (
        <div className="flex flex-col">
            <CameraPanel device={device} />
            <div className="mt-4 flex justify-center">
                <button
                    onClick={onToggle}
                    className={`px-4 py-2 text-sm font-semibold rounded-lg transition-colors ${
                        isOn 
                            ? "bg-rose-500/20 text-rose-400 hover:bg-rose-500/30 border border-rose-500/50" 
                            : "bg-emerald-500/20 text-emerald-400 hover:bg-emerald-500/30 border border-emerald-500/50"
                    }`}
                >
                    {isOn ? "Stop stream" : "Start stream"}
                </button>
            </div>
        </div>
      ) : null}

      {device.type === "SMART_SWITCH" || device.type === "SAFETY_HAZARD" || device.type === "OUTLET" || device.type === "LIGHTING" || device.type === "MULTI_SWITCH" ? (
        <div className="mt-5 flex justify-center">
          <button
            onClick={onToggle}
            className={`relative flex h-14 w-14 items-center justify-center rounded-full border-2 transition-all cursor-pointer outline-none hover:scale-105 active:scale-95 ${
              isOn
                ? "border-emerald-400 bg-emerald-500/20 shadow-[0_0_15px_rgba(52,211,153,0.4)]"
                : "border-slate-600 bg-slate-800 hover:bg-slate-700"
            }`}
            aria-label="Toggle Power"
          >
            <div
              className={`h-6 w-6 rounded-full transition-colors ${
                isOn ? "bg-emerald-400" : "bg-slate-600"
              }`}
            />
          </button>
        </div>
      ) : null}
    </div>
  );
}
