"use client";

import { useMemo, useState } from "react";
import { useFirebasePath } from "@/hooks/useFirebasePath";
import { flattenDevices } from "@/lib/devices";
import ApplianceCard from "@/components/ApplianceCard";
import { database } from "@/lib/firebase";
import { ref, update, push, remove, set } from "firebase/database";

export default function SimulatorDashboard() {
  const { data: floors, loading, error, connected } = useFirebasePath(
    "house/floors"
  );
  const [floorFilter, setFloorFilter] = useState("all");
  const [addDeviceModal, setAddDeviceModal] = useState(null);
  const [addDeviceType, setAddDeviceType] = useState("LIGHTING");

  const floorList = useMemo(() => {
    if (!floors) return [];
    const list = Object.entries(floors).map(([id, floor]) => ({
      id,
      name: floor?.name || id,
      gridCols: floor?.gridCols || 3,
      gridRows: floor?.gridRows || 3,
    }));

    // Sort so newest push IDs are first, and seeded floors are last
    list.sort((a, b) => {
      const isPushA = a.id.startsWith('-');
      const isPushB = b.id.startsWith('-');

      if (isPushA && !isPushB) return -1;
      if (!isPushA && isPushB) return 1;

      if (isPushA && isPushB) {
        // newer push ID is lexicographically greater. We want newest first.
        return b.id.localeCompare(a.id);
      }

      // For seeded (floor_1, floor_2), sort descending so floor_2 is above floor_1
      return b.id.localeCompare(a.id);
    });

    return list;
  }, [floors]);

  const handleToggle = async (device) => {
    const newStatus = device.status === "ON" ? "OFF" : "ON";
    const updates = {};
    updates[`house/floors/${device.floorId}/devices/${device.id}/status`] = newStatus;
    if ((device.type === "SMART_SWITCH" || device.type === "SAFETY_HAZARD") && newStatus === "ON") {
      // Create a timestamp outside of the render cycle
      updates[`house/floors/${device.floorId}/devices/${device.id}/last_turned_on_timestamp`] = new Date().getTime();
    }
    try {
      await update(ref(database), updates);
    } catch (error) {
      console.error("Error updating device status:", error);
    }
  };

  const handleSwitchToggle = async (device, switchId, currentlyOn) => {
    const newStatus = currentlyOn ? "OFF" : "ON";
    const updates = {};
    updates[`house/floors/${device.floorId}/devices/${device.id}/switches/${switchId}/status`] = newStatus;
    try {
      await update(ref(database), updates);
    } catch (e) {
      console.error("Failed to toggle switch", e);
    }
  };

  const handleSaveSchedule = async (device, isScheduled, turnOn, turnOff) => {
    const updates = {};
    updates[`house/floors/${device.floorId}/devices/${device.id}/is_scheduled`] = isScheduled;
    if (isScheduled) {
      updates[`house/floors/${device.floorId}/devices/${device.id}/schedule/turn_on_time`] = turnOn;
      updates[`house/floors/${device.floorId}/devices/${device.id}/schedule/turn_off_time`] = turnOff;
    }
    try {
      await update(ref(database), updates);
    } catch (e) {
      console.error("Failed to save schedule", e);
    }
  };

  const handleSaveCutoff = async (device, enableCutoff, duration) => {
    const updates = {};
    updates[`house/floors/${device.floorId}/devices/${device.id}/auto_cutoff_enabled`] = enableCutoff;
    if (enableCutoff) {
      updates[`house/floors/${device.floorId}/devices/${device.id}/max_on_duration_seconds`] = duration;
    }
    try {
      await update(ref(database), updates);
    } catch (e) {
      console.error("Failed to save cutoff", e);
    }
  };

  const handleAddFloor = async () => {
    const name = window.prompt("Enter new floor name:");
    if (!name || name.trim() === "") return;
    try {
      const floorsRef = ref(database, "house/floors");
      const newFloorRef = push(floorsRef);
      await set(newFloorRef, {
        id: newFloorRef.key,
        name: name.trim(),
        gridCols: 3,
        gridRows: 3,
      });
    } catch (e) {
      console.error("Failed to add floor", e);
    }
  };

  const handleDeleteFloor = async (floorId, floorName) => {
    if (!window.confirm(`Are you sure you want to delete ${floorName}?`)) return;
    try {
      await remove(ref(database, `house/floors/${floorId}`));
      if (floorFilter === floorId) setFloorFilter("all");
    } catch (e) {
      console.error("Failed to delete floor", e);
    }
  };

  const handleDeleteDevice = async (device) => {
    if (!window.confirm(`Delete ${device.name}?`)) return;
    try {
      await remove(ref(database, `house/floors/${device.floorId}/devices/${device.id}`));
    } catch (e) {
      console.error("Failed to delete device", e);
    }
  };

  const submitAddDevice = async (e) => {
    e.preventDefault();
    if (!addDeviceModal) return;

    const form = new FormData(e.target);
    const name = form.get("name");
    const type = form.get("type");

    const newDevice = {
      name,
      type: addDeviceType,
      status: "OFF",
      grid_position: { x: addDeviceModal.x, y: addDeviceModal.y }
    };

    if (addDeviceType === "SMART_SWITCH") {
      const enableCutoff = form.get("enableCutoff") === "on";
      newDevice.auto_cutoff_enabled = enableCutoff;
      if (enableCutoff) {
        newDevice.max_on_duration_seconds = parseInt(form.get("maxDuration") || "1800", 10);
      }
    } else if (addDeviceType === "LIGHTING") {
      const enableSchedule = form.get("enableSchedule") === "on";
      newDevice.is_scheduled = enableSchedule;
      if (enableSchedule) {
        newDevice.schedule = { turn_on_time: "18:00", turn_off_time: "22:00" };
      }
    } else if (addDeviceType === "MULTI_SWITCH") {
      newDevice.switches = {
        sw1: { name: "Switch 1", status: "OFF" },
        sw2: { name: "Switch 2", status: "OFF" }
      };
    }

    try {
      const deviceRef = push(ref(database, `house/floors/${addDeviceModal.floorId}/devices`));
      newDevice.id = deviceRef.key;
      await set(deviceRef, newDevice);
      setAddDeviceModal(null);
    } catch (err) {
      console.error("Failed to add device", err);
    }
  };

  const devices = useMemo(() => {
    const all = flattenDevices(floors);
    if (floorFilter === "all") return all;
    return all.filter((d) => d.floorId === floorFilter);
  }, [floors, floorFilter]);

  const isSandwichView = floorFilter === "all";

  return (
    <div className="min-h-screen bg-[radial-gradient(ellipse_at_top,#1e293b_0%,#020617_55%)] text-slate-100 overflow-hidden">
      <header className="border-b border-slate-800/80 bg-slate-950/70 backdrop-blur sticky top-0 z-50">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white sm:text-3xl bg-clip-text text-transparent bg-gradient-to-r from-cyan-400 to-emerald-400">
              Smart Home Controller
            </h1>
            <p className="mt-1 text-sm text-slate-400">
              Device Controlling & Monitoring Dashboard
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-4">
            <span
              className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold shadow-sm ${connected
                ? "bg-emerald-500/20 text-emerald-300 ring-1 ring-emerald-500/50"
                : "bg-rose-500/20 text-rose-300 ring-1 ring-rose-500/50"
                }`}
            >
              <span
                className={`h-2 w-2 rounded-full ${connected ? "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]" : "bg-rose-400"
                  }`}
              />
              {connected ? "System Online" : "Offline"}
            </span>
            <div className="relative">
              <select
                className="appearance-none rounded-lg border border-slate-700 bg-slate-900/80 px-4 py-2 pr-8 text-sm text-slate-100 outline-none focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 backdrop-blur-md transition-all cursor-pointer"
                value={floorFilter}
                onChange={(e) => {
                  if (e.target.value === "add_new_floor") {
                    handleAddFloor();
                  } else {
                    setFloorFilter(e.target.value);
                  }
                }}
              >
                <option value="all">All Floors</option>
                {floorList.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name}
                  </option>
                ))}
                <option value="add_new_floor" className="font-bold text-emerald-400">+ Add New Floor</option>
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-slate-400">
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7"></path></svg>
              </div>
            </div>
          </div>
        </div>
      </header>

      <main className="flex-1 flex flex-col items-center justify-start p-8 min-h-[calc(100vh-100px)]">
        {loading ? (
          <div className="flex items-center justify-center h-64">
            <div className="h-12 w-12 rounded-full border-4 border-cyan-500/30 border-t-cyan-500 animate-spin"></div>
          </div>
        ) : null}

        {error ? (
          <div className="rounded-lg border border-rose-500/40 bg-rose-950/40 p-4 text-rose-200">
            <h3 className="font-semibold mb-1">Connection Error</h3>
            <p className="text-sm opacity-90">{error}</p>
          </div>
        ) : null}

        {!loading && !error && (
          <div className="w-full max-w-7xl relative mx-auto pb-32">
            {isSandwichView ? (
              // 2D CROSS-SECTION (SIDE VIEW - BOOKSHELF)
              <div className="flex flex-col gap-6 w-full max-w-5xl mx-auto mt-8">
                {floorList.map((floor) => {
                  const floorDevices = devices.filter(d => d.floorId === floor.id);
                  return (
                    <div
                      key={floor.id}
                      className="relative w-full bg-slate-900 border-2 border-slate-700 rounded-xl overflow-hidden shadow-2xl p-6 pb-2 min-h-[160px] flex flex-col justify-end"
                    >
                      <div className="absolute top-2 left-2 bg-slate-800/90 px-3 py-1 rounded text-xs font-bold text-cyan-400 z-20 shadow">
                        {floor.name}
                      </div>
                      <button
                        onClick={() => handleDeleteFloor(floor.id, floor.name)}
                        className="absolute top-2 right-2 bg-slate-800/90 p-1.5 rounded text-rose-400 hover:text-rose-300 hover:bg-slate-700 transition z-20 shadow outline-none"
                        title="Delete Floor"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                      </button>

                      {/* Devices mapped as items on a shelf */}
                      <div className="flex flex-row flex-wrap items-end gap-6 z-10 w-full relative">
                        {/* The shelf floor line */}
                        <div className="absolute bottom-0 left-0 right-0 h-1 bg-slate-600 rounded-full" />

                        {floorDevices.map(device => {
                          const isOn = device.status === 'ON';

                          return (
                            <div
                              key={device.id}
                              className={`relative flex flex-col items-center justify-center transition-all cursor-pointer z-10 mb-2 ${isOn ? 'scale-110' : 'hover:scale-105'}`}
                              onClick={() => handleToggle(device)}
                            >
                              <div className={`p-3 rounded-lg shadow-lg border flex flex-col items-center gap-2 bg-slate-800/90 min-w-[80px] ${isOn ? 'border-emerald-500 shadow-emerald-500/20' : 'border-slate-600'}`}>
                                <div className={`w-3 h-3 rounded-full ${isOn ? 'bg-emerald-400 shadow-[0_0_8px_#34d399]' : 'bg-slate-500'}`}></div>
                                <span className="text-xs font-medium whitespace-nowrap text-center">{device.name}</span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              // TOP VIEW (2D Grid + Sidebar)
              <div className="flex flex-col lg:flex-row gap-8 items-start animate-in fade-in duration-500">

                {/* 2D Grid Floor Plan */}
                <div className="w-full lg:w-2/3">
                  <div className="bg-slate-900/80 border border-slate-700/60 rounded-2xl p-6 shadow-2xl backdrop-blur-sm">
                    <div className="flex items-center justify-between mb-6">
                      <h2 className="text-xl font-semibold flex items-center gap-2">
                        <svg className="w-5 h-5 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z"></path></svg>
                        {(floorList.find(f => f.id === floorFilter) || {}).name || 'Floor'} View
                      </h2>
                      <button
                        onClick={() => handleDeleteFloor(floorFilter, (floorList.find(f => f.id === floorFilter) || {}).name)}
                        className="text-xs flex items-center gap-1 bg-rose-500/10 text-rose-400 px-3 py-1.5 rounded-lg hover:bg-rose-500/20 transition outline-none"
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                        Delete Floor
                      </button>
                    </div>

                    {floorList.filter(f => f.id === floorFilter).map(floor => {
                      const floorDevices = devices.filter(d => d.floorId === floor.id);
                      return (
                        <div
                          key={floor.id}
                          className="relative w-full aspect-[3/1] bg-slate-950 rounded-xl border border-slate-800 overflow-hidden"
                        >
                          {/* Grid Background */}
                          <div
                            className="absolute inset-0 grid"
                            style={{
                              gridTemplateColumns: `repeat(${floor.gridCols}, minmax(0, 1fr))`,
                              gridTemplateRows: `repeat(${floor.gridRows}, minmax(0, 1fr))`
                            }}
                          >
                            {Array.from({ length: floor.gridCols * floor.gridRows }).map((_, i) => (
                              <div key={i} className="border border-slate-800/50"></div>
                            ))}
                          </div>

                          {/* Devices on grid & Empty Slots */}
                          {Array.from({ length: floor.gridCols * floor.gridRows }).map((_, i) => {
                            const cx = i % floor.gridCols;
                            const cy = Math.floor(i / floor.gridCols);
                            const device = floorDevices.find(d =>
                              (d.grid_position?.x || 0) === cx &&
                              (d.grid_position?.y || 0) === cy
                            );

                            return (
                              <div
                                key={`cell-${cx}-${cy}`}
                                className="absolute"
                                style={{
                                  left: `${(cx / floor.gridCols) * 100}%`,
                                  top: `${(cy / floor.gridRows) * 100}%`,
                                  width: `${100 / floor.gridCols}%`,
                                  height: `${100 / floor.gridRows}%`,
                                }}
                              >
                                {device ? (
                                  <div
                                    className={`w-full h-full flex flex-col items-center justify-center transition-all cursor-pointer z-10 ${device.status === 'ON' ? 'scale-110' : 'hover:scale-105'}`}
                                    onClick={() => handleToggle(device)}
                                  >
                                    <div className={`p-2 rounded-lg shadow-lg border flex flex-col items-center gap-1 bg-slate-900/90 ${device.status === 'ON' ? 'border-emerald-500 shadow-emerald-500/20' : 'border-slate-700'}`}>
                                      <div className={`w-3 h-3 rounded-full ${device.status === 'ON' ? 'bg-emerald-400 shadow-[0_0_8px_#34d399]' : 'bg-slate-600'}`}></div>
                                      <span className="text-[10px] font-medium hidden sm:block whitespace-nowrap text-slate-200">{device.name}</span>
                                    </div>
                                  </div>
                                ) : (
                                  <div
                                    onClick={() => { setAddDeviceType("LIGHTING"); setAddDeviceModal({ floorId: floor.id, x: cx, y: cy }); }}
                                    className="w-full h-full flex items-center justify-center cursor-pointer hover:bg-slate-800/40 transition group"
                                  >
                                    <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-500 group-hover:text-emerald-400 group-hover:border-emerald-500/50 transition">
                                      <span className="text-lg font-bold leading-none">+</span>
                                    </div>
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )
                    })}
                  </div>
                </div>

                {/* List of devices (like original cards but refined) */}
                <div className="w-full lg:w-1/3">
                  <div className="flex items-center justify-between mb-6">
                    <h2 className="text-xl font-semibold">Device Controls</h2>
                    <span className="text-xs bg-slate-800 px-2 py-1 rounded text-slate-400">{devices.length} Devices</span>
                  </div>
                  <div className="grid grid-cols-1 gap-4">
                    {devices.map((device) => (
                      <ApplianceCard
                        key={`${device.floorId}-${device.id}`}
                        device={device}
                        onToggle={() => handleToggle(device)}
                        onSwitchToggle={(switchId, currentlyOn) => handleSwitchToggle(device, switchId, currentlyOn)}
                        onDelete={() => handleDeleteDevice(device)}
                        onSaveSchedule={(isScheduled, turnOn, turnOff) => handleSaveSchedule(device, isScheduled, turnOn, turnOff)}
                        onSaveCutoff={(enableCutoff, duration) => handleSaveCutoff(device, enableCutoff, duration)}
                      />
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Add Device Modal */}
        {addDeviceModal && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-slate-700 rounded-xl p-6 w-full max-w-md shadow-2xl">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-xl font-semibold text-emerald-400">Add New Device</h3>
                <button onClick={() => setAddDeviceModal(null)} className="text-slate-400 hover:text-white">✕</button>
              </div>

              <form onSubmit={submitAddDevice} className="flex flex-col gap-4">
                <div>
                  <label className="block text-sm text-slate-300 mb-1">Device Name</label>
                  <input name="name" required className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none focus:border-emerald-500" placeholder="e.g. Living Room Lamp" />
                </div>

                <div>
                  <label className="block text-sm text-slate-300 mb-1">Device Type</label>
                  <select
                    name="type"
                    value={addDeviceType}
                    onChange={(e) => setAddDeviceType(e.target.value)}
                    required
                    className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none focus:border-emerald-500"
                  >
                    <option value="LIGHTING">Lighting</option>
                    <option value="SMART_SWITCH">Smart Switch</option>
                    <option value="MULTI_SWITCH">Multi-Switch Gang</option>
                    <option value="SECURITY_CAMERA">Security Camera</option>
                  </select>
                </div>

                <div className="border-t border-slate-700 pt-4 mt-2">
                  {addDeviceType === "LIGHTING" && (
                    <label className="flex items-center gap-2 text-sm text-slate-300 mb-3 cursor-pointer">
                      <input type="checkbox" name="enableSchedule" className="rounded border-slate-600 bg-slate-800" />
                      Enable Default Schedule (Lighting)
                    </label>
                  )}

                  {addDeviceType === "SMART_SWITCH" && (
                    <div className="flex flex-col gap-2">
                      <label className="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                        <input type="checkbox" name="enableCutoff" className="rounded border-slate-600 bg-slate-800" defaultChecked={false} onChange={(e) => {
                          const input = document.getElementById("maxDurationInput");
                          if (input) {
                            input.style.display = e.target.checked ? "block" : "none";
                            input.required = e.target.checked;
                          }
                        }} />
                        Enable Auto-Cutoff Timer
                      </label>
                      <input id="maxDurationInput" type="number" name="maxDuration" defaultValue="1800" className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none focus:border-emerald-500" style={{ display: "none" }} placeholder="Max Duration (Seconds)" />
                    </div>
                  )}
                  {addDeviceType !== "LIGHTING" && addDeviceType !== "SMART_SWITCH" && (
                    <p className="text-sm text-slate-500 italic">No extra configurations needed for this device type.</p>
                  )}
                </div>

                <button type="submit" className="mt-4 w-full bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold py-2.5 rounded-lg transition-colors">
                  Add Device
                </button>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
