"use client";

import { useEffect, useState } from "react";
import { onValue, ref } from "firebase/database";
import { database } from "@/lib/firebase";

/**
 * Subscribe to a Realtime Database path. Unsubscribes on unmount.
 * @param {string} path
 * @returns {{ data: unknown, loading: boolean, error: string | null, connected: boolean }}
 */
export function useFirebasePath(path) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [connected, setConnected] = useState(true);

  useEffect(() => {
    if (!path) return undefined;

    setLoading(true);
    const dbRef = ref(database, path);
    const unsubscribe = onValue(
      dbRef,
      (snapshot) => {
        setData(snapshot.val());
        setLoading(false);
        setError(null);
        setConnected(true);
      },
      (err) => {
        setError(err.message || "Firebase connection error");
        setLoading(false);
        setConnected(false);
      }
    );

    const connRef = ref(database, ".info/connected");
    const unsubConn = onValue(connRef, (snap) => {
      setConnected(Boolean(snap.val()));
    });

    return () => {
      unsubscribe();
      unsubConn();
    };
  }, [path]);

  return { data, loading, error, connected };
}
