const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const SAMPLE_SHA256 = "a".repeat(64);
const OTHER_SHA256 = "b".repeat(64);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function makePrivateUser(uid, created = pastDate(5000)) {
  return {
    userId: uid,
    email: `${uid}@example.com`,
    interestedIn: "Everyone",
    minAgePref: 21,
    maxAgePref: 38,
    maxDistanceKm: 50,
    verifiedOnlyFilter: false,
    exactLatitude: 37.7749,
    exactLongitude: -122.4194,
    createdAt: created,
    updatedAt: created,
  };
}

function makePublicProfile(uid, created = pastDate(5000)) {
  return {
    userId: uid,
    displayName: uid === ALICE_UID ? "Alice" : "Bob",
    age: 27,
    gender: uid === ALICE_UID ? "Woman" : "Man",
    bio: "Coffee walks, architecture, and weekend trail runs.",
    cityLabel: "San Francisco",
    coarseLatitude: 37.77,
    coarseLongitude: -122.42,
    geohash: "9q8yyk8",
    geohash3: "9q8",
    geohash4: "9q8y",
    interests: ["Coffee", "Hiking", "Photography"],
    avatarSeed: uid,
    discoverable: true,
    verificationStatus: "UNVERIFIED",
    verificationScore: 30,
    createdAt: created,
    updatedAt: created,
  };
}

function makeGeoCellProfile(uid, cellId, created = pastDate(5000)) {
  return {
    userId: uid,
    geohash: "9q8yyk8",
    cellPrecision: cellId.length,
    displayName: uid === ALICE_UID ? "Alice" : "Bob",
    age: 27,
    gender: uid === ALICE_UID ? "Woman" : "Man",
    bio: "Coffee walks, architecture, and weekend trail runs.",
    cityLabel: "San Francisco",
    coarseLatitude: 37.77,
    coarseLongitude: -122.42,
    interests: ["Coffee", "Hiking", "Photography"],
    avatarSeed: uid,
    discoverable: true,
    verificationStatus: "UNVERIFIED",
    verificationScore: 30,
    createdAt: created,
    updatedAt: created,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections including geoCells", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("publicProfiles").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("geoCells").doc("9q8y").collection("profiles").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("swipes").where("fromUserId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("matches").where("participantUids", "array-contains", ALICE_UID).get());
});

test("Private users collection enforces owner-only PII isolation and immutable createdAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makePrivateUser(ALICE_UID, created))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      maxDistanceKm: 100,
      updatedAt: pastDate(1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      maxDistanceKm: 80,
      verifiedOnlyFilter: true,
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
});

test("GeoHash cells enforce prefix parity, discoverable list filtering, and owner-only writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Alice indexes herself into precision-4 and precision-3 GeoHash cells
  await assertSucceeds(
    aliceDb.collection("geoCells").doc("9q8y").collection("profiles").doc(ALICE_UID)
      .set(makeGeoCellProfile(ALICE_UID, "9q8y", created))
  );
  await assertSucceeds(
    aliceDb.collection("geoCells").doc("9q8").collection("profiles").doc(ALICE_UID)
      .set(makeGeoCellProfile(ALICE_UID, "9q8", created))
  );

  // Spoofing into a non-matching GeoHash cell (e.g. Tokyo "xn76") must FAIL
  await assertFails(
    aliceDb.collection("geoCells").doc("xn76").collection("profiles").doc(ALICE_UID)
      .set(makeGeoCellProfile(ALICE_UID, "xn76", created))
  );

  // Unfiltered list on geoCells fails; filtered discoverable==true succeeds
  await assertFails(
    bobDb.collection("geoCells").doc("9q8y").collection("profiles").get()
  );
  await assertSucceeds(
    bobDb.collection("geoCells").doc("9q8y").collection("profiles")
      .where("discoverable", "==", true)
      .get()
  );

  // Bob cannot overwrite or delete Alice's GeoCell entry
  await assertFails(
    bobDb.collection("geoCells").doc("9q8y").collection("profiles").doc(ALICE_UID).delete()
  );
});

test("Public profiles and geoCells block spoofed verification without audit record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("publicProfiles").doc(ALICE_UID).set(makePublicProfile(ALICE_UID, created))
  );
  await assertSucceeds(
    aliceDb.collection("geoCells").doc("9q8y").collection("profiles").doc(ALICE_UID)
      .set(makeGeoCellProfile(ALICE_UID, "9q8y", created))
  );

  await assertFails(bobDb.collection("publicProfiles").get());
  await assertSucceeds(
    bobDb.collection("publicProfiles").where("discoverable", "==", true).get()
  );

  // Shadow update / spoofed verificationStatus without verification record must FAIL
  await assertFails(
    aliceDb.collection("publicProfiles").doc(ALICE_UID).update({
      verificationStatus: "VERIFIED",
      verificationId: "ver_fake",
      verificationScore: 95,
      verificationHash: SAMPLE_SHA256,
      verifiedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Atomic batch creating verification audit record + updating publicProfile & geoCell SUCCEEDS
  const verId = "ver_1001";
  const nowTs = pastDate(1000);
  const batch = aliceDb.batch();
  batch.set(
    aliceDb.collection("users").doc(ALICE_UID).collection("verifications").doc(verId),
    {
      userId: ALICE_UID,
      challengeCode: "SMILE_CENTER",
      challengePrompt: "Look directly at the camera with a natural smile",
      faceCount: 1,
      faceConfidence: 0.88,
      eyesDistancePx: 64.5,
      photoSha256: SAMPLE_SHA256,
      locationVerified: true,
      googleVerified: true,
      comparedPhotoCount: 2,
      photoSimilarityScore: 0.84,
      livenessPassed: true,
      faceMatchPassed: true,
      createdAt: nowTs,
    }
  );
  batch.update(aliceDb.collection("publicProfiles").doc(ALICE_UID), {
    verificationStatus: "VERIFIED",
    verificationId: verId,
    verificationScore: 95,
    verificationHash: SAMPLE_SHA256,
    verifiedAt: nowTs,
    updatedAt: nowTs,
  });
  batch.update(aliceDb.collection("geoCells").doc("9q8y").collection("profiles").doc(ALICE_UID), {
    verificationStatus: "VERIFIED",
    verificationId: verId,
    verificationScore: 95,
    verificationHash: SAMPLE_SHA256,
    verifiedAt: nowTs,
    updatedAt: nowTs,
  });
  await assertSucceeds(batch.commit());

  // Catfish / low similarity verification record must FAIL
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("verifications").doc("ver_catfish").set({
      userId: ALICE_UID,
      challengeCode: "SMILE_CENTER",
      challengePrompt: "Look directly at the camera with a natural smile",
      faceCount: 1,
      faceConfidence: 0.88,
      eyesDistancePx: 64.5,
      photoSha256: SAMPLE_SHA256,
      locationVerified: true,
      googleVerified: true,
      comparedPhotoCount: 1,
      photoSimilarityScore: 0.38,
      livenessPassed: true,
      faceMatchPassed: false,
      createdAt: pastDate(700),
    })
  );

  // Hash mismatch between verification record and public profile must FAIL
  const badBatch = aliceDb.batch();
  const verId2 = "ver_1002";
  badBatch.set(
    aliceDb.collection("users").doc(ALICE_UID).collection("verifications").doc(verId2),
    {
      userId: ALICE_UID,
      challengeCode: "HEAD_TILT",
      challengePrompt: "Tilt your head slightly left",
      faceCount: 1,
      faceConfidence: 0.91,
      eyesDistancePx: 70.0,
      photoSha256: SAMPLE_SHA256,
      locationVerified: true,
      googleVerified: true,
      comparedPhotoCount: 1,
      photoSimilarityScore: 0.89,
      livenessPassed: true,
      faceMatchPassed: true,
      createdAt: pastDate(500),
    }
  );
  badBatch.update(aliceDb.collection("publicProfiles").doc(ALICE_UID), {
    verificationStatus: "VERIFIED",
    verificationId: verId2,
    verificationScore: 98,
    verificationHash: OTHER_SHA256,
    verifiedAt: pastDate(500),
    updatedAt: pastDate(500),
  });
  await assertFails(badBatch.commit());
});

test("Swipes, atomic mutual matches, and real-time chat isolation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const created = pastDate(20000);
    await adminDb.collection("publicProfiles").doc(ALICE_UID).set(makePublicProfile(ALICE_UID, created));
    await adminDb.collection("publicProfiles").doc(BOB_UID).set(makePublicProfile(BOB_UID, created));
    await adminDb.collection("publicProfiles").doc(CHARLIE_UID).set(makePublicProfile(CHARLIE_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("swipes").doc(`${ALICE_UID}_${BOB_UID}`).set({
      fromUserId: ALICE_UID,
      toUserId: BOB_UID,
      direction: "LIKE",
      createdAt: pastDate(5000),
    })
  );

  await assertFails(
    aliceDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).set({
      participantUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      createdAt: pastDate(4000),
      updatedAt: pastDate(4000),
    })
  );

  await assertSucceeds(
    bobDb.collection("swipes").where("toUserId", "==", BOB_UID).get()
  );

  const matchId = `${ALICE_UID}_${BOB_UID}`;
  const matchTs = pastDate(3000);
  const matchBatch = bobDb.batch();
  matchBatch.set(bobDb.collection("swipes").doc(`${BOB_UID}_${ALICE_UID}`), {
    fromUserId: BOB_UID,
    toUserId: ALICE_UID,
    direction: "LIKE",
    createdAt: matchTs,
  });
  matchBatch.set(bobDb.collection("matches").doc(matchId), {
    participantUids: [ALICE_UID, BOB_UID],
    createdBy: BOB_UID,
    createdAt: matchTs,
    updatedAt: matchTs,
  });
  await assertSucceeds(matchBatch.commit());

  await assertFails(charlieDb.collection("matches").doc(matchId).get());
  await assertFails(
    charlieDb.collection("matches").doc(matchId).collection("messages").doc("msg_bad").set({
      matchId,
      senderId: CHARLIE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      text: "Intruder message",
      messageType: "TEXT",
      createdAt: pastDate(1000),
    })
  );

  const chatTs = pastDate(1000);
  const msgBatch = aliceDb.batch();
  msgBatch.set(
    aliceDb.collection("matches").doc(matchId).collection("messages").doc("msg_1"),
    {
      matchId,
      senderId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      text: "Hey Bob! Loved your photography photos.",
      messageType: "TEXT",
      createdAt: chatTs,
    }
  );
  msgBatch.update(aliceDb.collection("matches").doc(matchId), {
    lastMessageText: "Hey Bob! Loved your photography photos.",
    lastMessageSenderId: ALICE_UID,
    lastMessageAt: chatTs,
    updatedAt: chatTs,
  });
  await assertSucceeds(msgBatch.commit());

  await assertSucceeds(
    bobDb.collection("matches").doc(matchId).collection("messages")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );
});

test("Presence tracking enforces owner-only writes, targeted get reads, and blocks mass listing", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertFails(unauthDb.collection("presence").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("presence").get());

  // Alice creates her online presence document
  await assertSucceeds(
    aliceDb.collection("presence").doc(ALICE_UID).set({
      userId: ALICE_UID,
      online: true,
      lastSeenAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Bob can read Alice's specific presence doc
  await assertSucceeds(bobDb.collection("presence").doc(ALICE_UID).get());

  // Bob cannot modify Alice's presence
  await assertFails(
    bobDb.collection("presence").doc(ALICE_UID).set({
      userId: ALICE_UID,
      online: false,
      lastSeenAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Alice transitions offline via set() overwrite (hits update rule)
  await assertSucceeds(
    aliceDb.collection("presence").doc(ALICE_UID).set({
      userId: ALICE_UID,
      online: false,
      lastSeenAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );

  // Extra fields are rejected
  await assertFails(
    aliceDb.collection("presence").doc(ALICE_UID).set({
      userId: ALICE_UID,
      online: true,
      lastSeenAt: pastDate(100),
      updatedAt: pastDate(100),
      adminFlag: true,
    })
  );
});

test("FCM tokens enforce strict owner-only registration and platform constraints", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const tokenDoc = "tok_device_001";

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fcmTokens").doc(tokenDoc).set({
      userId: ALICE_UID,
      token: "fcm_sample_token_abcdef_1234567890",
      platform: "android",
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot read or overwrite Alice's FCM token
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("fcmTokens").doc(tokenDoc).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("fcmTokens").doc(tokenDoc).set({
      userId: ALICE_UID,
      token: "hijacked_token_abcdef_1234567890",
      platform: "android",
      updatedAt: pastDate(300),
    })
  );
});

test("User blocks enforce owner creation, block swipes/messages between users, and allow unblock by blocker", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const created = pastDate(20000);
    await adminDb.collection("publicProfiles").doc(ALICE_UID).set(makePublicProfile(ALICE_UID, created));
    await adminDb.collection("publicProfiles").doc(BOB_UID).set(makePublicProfile(BOB_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const blockId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("blocks").doc(blockId).set({
      blockerUid: ALICE_UID,
      blockedUid: BOB_UID,
      reason: "Safety preference",
      createdAt: pastDate(2000),
    })
  );

  // Third party cannot inspect block records
  await assertFails(charlieDb.collection("blocks").doc(blockId).get());

  // Blocked participant CAN query blocks where blockedUid == self, or read specific doc
  await assertSucceeds(bobDb.collection("blocks").where("blockedUid", "==", BOB_UID).get());
  await assertSucceeds(aliceDb.collection("blocks").where("blockerUid", "==", ALICE_UID).get());

  // Bob cannot swipe on Alice after being blocked
  await assertFails(
    bobDb.collection("swipes").doc(`${BOB_UID}_${ALICE_UID}`).set({
      fromUserId: BOB_UID,
      toUserId: ALICE_UID,
      direction: "LIKE",
      createdAt: pastDate(1000),
    })
  );

  // Bob cannot delete Alice's block, only Alice can unblock
  await assertFails(bobDb.collection("blocks").doc(blockId).delete());
  await assertSucceeds(aliceDb.collection("blocks").doc(blockId).delete());

  // After unblocking, swipe works again
  await assertSucceeds(
    bobDb.collection("swipes").doc(`${BOB_UID}_${ALICE_UID}`).set({
      fromUserId: BOB_UID,
      toUserId: ALICE_UID,
      direction: "LIKE",
      createdAt: pastDate(500),
    })
  );
});

test("Chat read receipts, typing indicators, disappearing timers, and call signaling enforce participant isolation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const created = pastDate(20000);
    await adminDb.collection("publicProfiles").doc(ALICE_UID).set(makePublicProfile(ALICE_UID, created));
    await adminDb.collection("publicProfiles").doc(BOB_UID).set(makePublicProfile(BOB_UID, created));
    await adminDb.collection("swipes").doc(`${ALICE_UID}_${BOB_UID}`).set({
      fromUserId: ALICE_UID,
      toUserId: BOB_UID,
      direction: "LIKE",
      createdAt: created,
    });
    await adminDb.collection("swipes").doc(`${BOB_UID}_${ALICE_UID}`).set({
      fromUserId: BOB_UID,
      toUserId: ALICE_UID,
      direction: "LIKE",
      createdAt: created,
    });
    await adminDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).set({
      participantUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const matchId = `${ALICE_UID}_${BOB_UID}`;

  // Alice sends a disappearing photo message
  const msgRef = aliceDb.collection("matches").doc(matchId).collection("messages").doc("msg_photo_1");
  await assertSucceeds(
    msgRef.set({
      matchId,
      senderId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      text: "Sunset coffee view",
      messageType: "PHOTO",
      mediaDataUrl: "data:image/jpeg;base64,SAMPLE_DATA",
      readByUids: [ALICE_UID],
      disappearingSeconds: 30,
      viewOnce: true,
      openedByRecipient: false,
      createdAt: pastDate(1500),
    })
  );

  // Bob marks the message read and opened
  await assertSucceeds(
    bobDb.collection("matches").doc(matchId).collection("messages").doc("msg_photo_1").update({
      readByUids: [ALICE_UID, BOB_UID],
      readAt: pastDate(500),
      openedByRecipient: true,
      expiresAtMs: Date.now() + 10000,
    })
  );

  // Bob cannot rewrite the original message text when updating read receipt
  await assertFails(
    bobDb.collection("matches").doc(matchId).collection("messages").doc("msg_photo_1").update({
      text: "Tampered text",
      readByUids: [ALICE_UID, BOB_UID],
    })
  );

  // Alice updates typing indicator; Charlie cannot spoof typing
  await assertSucceeds(
    aliceDb.collection("matches").doc(matchId).collection("typing").doc(ALICE_UID).set({
      matchId,
      userId: ALICE_UID,
      isTyping: true,
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    bobDb.collection("matches").doc(matchId).collection("typing").doc(ALICE_UID).set({
      matchId,
      userId: ALICE_UID,
      isTyping: false,
      updatedAt: pastDate(300),
    })
  );

  // Alice initiates an in-app video call; Bob answers it; Charlie is denied
  const callRef = aliceDb.collection("matches").doc(matchId).collection("calls").doc("call_001");
  await assertSucceeds(
    callRef.set({
      matchId,
      callerUid: ALICE_UID,
      calleeUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      callerMuted: false,
      calleeMuted: false,
      callerCameraOn: true,
      calleeCameraOn: true,
      createdAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    bobDb.collection("matches").doc(matchId).collection("calls").doc("call_001").update({
      status: "ACTIVE",
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    charlieDb.collection("matches").doc(matchId).collection("calls").doc("call_001").get()
  );

  // Audio chunk streaming is participant-only and senderUid must match auth.uid
  const chunkRef = aliceDb.collection("matches").doc(matchId).collection("calls").doc("call_001").collection("audioChunks").doc("chunk_alice_0");
  await assertSucceeds(
    chunkRef.set({
      matchId,
      callId: "call_001",
      senderUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      seq: 0,
      sampleRateHz: 8000,
      audioBase64: "AQIDBA==",
      createdAt: pastDate(100),
    })
  );
  await assertSucceeds(
    bobDb.collection("matches").doc(matchId).collection("calls").doc("call_001").collection("audioChunks").doc("chunk_alice_0").get()
  );
  await assertFails(
    charlieDb.collection("matches").doc(matchId).collection("calls").doc("call_001").collection("audioChunks").doc("chunk_alice_0").get()
  );

  // Alice can attach a short video clip (<=20s); >920KB base64 is rejected
  await assertSucceeds(
    aliceDb.collection("matches").doc(matchId).collection("messages").doc("msg_video_1").set({
      matchId,
      senderId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      text: "Quick cafe clip",
      messageType: "VIDEO",
      mediaDataUrl: "data:video/mp4;base64,AAAAIGZ0eXBpc29t",
      mediaDurationSec: 12,
      readByUids: [ALICE_UID],
      createdAt: pastDate(50),
    })
  );
  await assertFails(
    aliceDb.collection("matches").doc(matchId).collection("messages").doc("msg_video_too_big").set({
      matchId,
      senderId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      text: "Too large",
      messageType: "VIDEO",
      mediaDataUrl: "x".repeat(930000),
      mediaDurationSec: 15,
      readByUids: [ALICE_UID],
      createdAt: pastDate(40),
    })
  );
});

test("Moderation reports enforce reporter identity, disallow self-reporting, and remain immutable", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const created = pastDate(20000);
    await adminDb.collection("publicProfiles").doc(ALICE_UID).set(makePublicProfile(ALICE_UID, created));
    await adminDb.collection("publicProfiles").doc(BOB_UID).set(makePublicProfile(BOB_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const reportId = "rep_001";
  await assertSucceeds(
    aliceDb.collection("reports").doc(reportId).set({
      reporterUid: ALICE_UID,
      reportedUid: BOB_UID,
      reason: "Harassment or abusive messages",
      details: "Sent unsolicited inappropriate message.",
      sourceSurface: "CHAT",
      matchId: `${ALICE_UID}_${BOB_UID}`,
      createdAt: pastDate(500),
    })
  );

  // Self-report fails
  await assertFails(
    aliceDb.collection("reports").doc("rep_self").set({
      reporterUid: ALICE_UID,
      reportedUid: ALICE_UID,
      reason: "Fake profile",
      sourceSurface: "PROFILE",
      createdAt: pastDate(400),
    })
  );

  // Reporter can view own report; reported user cannot read or alter it
  await assertSucceeds(aliceDb.collection("reports").where("reporterUid", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("reports").doc(reportId).get());
  await assertFails(aliceDb.collection("reports").doc(reportId).delete());
});
