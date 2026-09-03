const users = require("./users");

module.exports = (io, socket) => {
  const authenticatedUserId = socket.data.userId;

  if (authenticatedUserId) {
    users.registerUser(authenticatedUserId, socket.id);
    console.log(`👤 Usuario registrado automáticamente por JWT: ${authenticatedUserId} -> socket: ${socket.id}`);
  }

  const registerAuthenticatedUser = () => {
    if (authenticatedUserId) {
      users.registerUser(authenticatedUserId, socket.id);
      console.log(`👤 Socket registrado por identidad JWT: ${authenticatedUserId} -> ${socket.id}`);
    }
  };

  // Support both historical register-user and the Android client's register_user event.
  socket.on("register-user", registerAuthenticatedUser);
  socket.on("register_user", registerAuthenticatedUser);

  const forwardSignaling = (eventName, data, targetEventName = eventName) => {
    if (!data || typeof data !== "object") return;

    const receiverId = data.receiverId || data.targetUserId || data.callerId;
    if (!authenticatedUserId || !receiverId || receiverId === authenticatedUserId) return;

    // Never trust client-supplied sender identity.
    const payload = { ...data, senderId: authenticatedUserId };
    if (payload.callerId && eventName !== "call_accept" && eventName !== "call_request") {
      payload.callerId = authenticatedUserId;
    }

    console.log(`📞 [${eventName}] de ${authenticatedUserId} para ${receiverId}`);
    const targetSocketId = users.getUserSocket(receiverId);
    if (targetSocketId) {
      io.to(targetSocketId).emit(targetEventName, payload);
    } else {
      console.log(`⚠️ Receptor ${receiverId} no conectado para ${eventName}`);
      // Incoming call to an offline recipient: wake the device via FCM so it rings.
      if (eventName === "call_request") {
        sendIncomingCallPush(receiverId, payload).catch((err) =>
          console.warn("⚠️ sendIncomingCallPush failed:", err.message)
        );
      }
    }
  };

  /**
   * Fires an FCM push (via the Supabase `send-push` edge function) so an
   * offline recipient's phone rings for an incoming call. The push carries the
   * caller id/name/type so the app can render the incoming-call screen + ringtone.
   * No-op if the server isn't configured with the Supabase URL / internal secret.
   */
  async function sendIncomingCallPush(receiverId, payload) {
    const supabaseUrl = process.env.SUPABASE_URL;
    const internalSecret = process.env.EDGE_INTERNAL_SECRET;
    const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
    if (!supabaseUrl || !internalSecret) {
      console.warn("⚠️ Incoming-call FCM skipped: SUPABASE_URL/EDGE_INTERNAL_SECRET not set on server.");
      return;
    }
    const callerName = payload.callerName || "Panalink";
    const callType = payload.callType || "voice";
    const callerId = payload.callerId || authenticatedUserId;

    const body = {
      user_id: receiverId,
      title: callerName,
      body: callType === "video" ? "Videollamada entrante" : "Llamada entrante",
      channel: "panalink_calls_v3",
      data: {
        notification_type: "llamada_entrante",
        callerId,
        callerName,
        callType,
      },
    };
    const headers = { "Content-Type": "application/json", "x-internal-secret": internalSecret };
    if (serviceRoleKey) headers["Authorization"] = `Bearer ${serviceRoleKey}`;

    const res = await fetch(`${supabaseUrl}/functions/v1/send-push`, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      console.warn(`⚠️ send-push responded ${res.status}: ${await res.text()}`);
    } else {
      console.log(`🔔 FCM incoming-call push sent to ${receiverId} (${callType})`);
    }
  }

  // Android signaling contract.
  socket.on("call_request", (data) => {
    forwardSignaling("call_request", data, "incoming_call");
  });

  socket.on("webrtc_offer", (data) => {
    forwardSignaling("webrtc_offer", data, "webrtc_offer");
  });

  socket.on("webrtc_answer", (data) => {
    forwardSignaling("webrtc_answer", data, "webrtc_answer");
  });

  socket.on("call_accept", (data) => {
    forwardSignaling("call_accept", data, "call_accepted");
  });

  // Backward-compatible server event names.
  socket.on("call_offer", (data) => {
    forwardSignaling("call_offer", data, "incoming_call");
  });

  socket.on("call_answer", (data) => {
    forwardSignaling("call_answer", data, "call_answer");
  });

  socket.on("ice_candidate", (data) => {
    forwardSignaling("ice_candidate", data, "ice_candidate");
  });

  socket.on("call_reject", (data) => {
    forwardSignaling("call_reject", data, "call_reject");
  });

  socket.on("call_end", (data) => {
    forwardSignaling("call_end", data, "call_end");
  });

  socket.on("call_busy", (data) => {
    forwardSignaling("call_busy", data, "call_busy");
  });

  socket.on("user_online", () => {
    console.log(`user_online for user ${authenticatedUserId}`);
  });

  socket.on("typing", () => {
    console.log(`typing from user ${authenticatedUserId}`);
  });

  socket.on("message_seen", () => {
    console.log(`message_seen from user ${authenticatedUserId}`);
  });

  socket.on("ping_server", (_data, callback) => {
    if (typeof callback === "function") {
      callback({ status: "pong", userId: authenticatedUserId });
    }
  });

  socket.on("disconnect", () => {
    users.removeUser(socket.id);
    console.log(`🔌 Socket desconectado: ${socket.id} (usuario: ${authenticatedUserId})`);
  });
};
