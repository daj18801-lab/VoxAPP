const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_owner_001";
const ADMIN_EMAIL = "daj18801@gmail.com";
const ALICE_UID = "alice_host_123";
const BOB_UID = "bob_guest_456";
const SUPPORT_UID = "support_staff_789";
const SUPPORT_EMAIL = "support@voxstage.test";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
}

function futureDate(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

function validProfilePayload(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    displayName: "Studio Host",
    handle: "studio_host",
    bio: "Live on VoxStage",
    avatarColorHex: "#6C63FF",
    pushToTalkPreferred: true,
    darkThemePreferred: true,
    invisibleModePreferred: false,
    globallyBanned: false,
    disableCreateRoom: false,
    disableEnterRoom: false,
    disableMic: false,
    disableVideo: false,
    suspendedUntilMillis: 0,
    suspensionReason: "",
    blockedUserIds: [],
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validRoomPayload(hostId, overrides = {}) {
  const ts = pastDate(5000);
  return {
    hostId,
    hostName: "Alice",
    hostTier: "USER",
    title: "Late Night Acoustic Jam",
    topic: "Respect everyone on stage.",
    category: "MUSIC",
    coverKey: "MUSIC",
    visibility: "PUBLIC",
    status: "LIVE",
    videoEnabled: true,
    autoModEnabled: true,
    autoModBlockMode: false,
    linksBlocked: true,
    roomLocked: false,
    stageMicLocked: false,
    slowModeSeconds: 3,
    maxSpeakers: 6,
    participantCount: 0,
    moderatorIds: [hostId],
    bannedUserIds: [],
    mutedUserIds: [],
    blockedWords: [],
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validParticipantPayload(uid, roomId, overrides = {}) {
  const ts = pastDate(3000);
  return {
    userId: uid,
    roomId,
    displayName: "Member",
    avatarColorHex: "#00C4B4",
    role: "SPEAKER",
    staffTier: "USER",
    invisible: false,
    micActive: false,
    camActive: false,
    handRaised: false,
    mutedByMod: false,
    speakingLevel: 0.0,
    joinedAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validMessagePayload(roomId, senderId, overrides = {}) {
  const ts = pastDate(2000);
  return {
    roomId,
    senderId,
    senderName: "Bob",
    senderRole: "SPEAKER",
    senderTier: "USER",
    text: "Hello everyone on the stage!",
    messageType: "TEXT",
    flaggedByAutoMod: false,
    deletedByMod: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
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

// 1. UNAUTHENTICATED DENIAL & STRICT READ-ONLY PUBLIC TRIAL PREVIEW
test("Unauthenticated users cannot read private collections or locked photos, and cannot write anywhere", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastDate(3000);
  const sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      adultVerified: true,
      birthYear: 1995,
    })
  );
  await assertSucceeds(aliceDb.collection("rooms").doc("pub_room").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("rooms").doc("priv_room").set({
      ...validRoomPayload(ALICE_UID),
      visibility: "PRIVATE",
    })
  );
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_unlocked").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorTier: "USER",
      text: "Unlocked public update",
      imageBase64: sampleBase64,
      locked: false,
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_locked").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorTier: "USER",
      text: "Locked photo",
      imageBase64: sampleBase64,
      locked: true,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("appConfig").doc("global").set({
      bannerTitle: "Welcome",
      bannerMessage: "New update live!",
      bannerEnabled: true,
      trialLoginsEnabled: true,
      updatedByName: "Admin",
      updatedAt: ts,
    })
  );

  // Unauthenticated / trial users CANNOT read private collections or locked photos
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").doc("priv_room").get());
  await assertFails(unauthDb.collection("profilePosts").doc("post_locked").get());
  await assertFails(unauthDb.collection("profilePosts").get());
  await assertFails(unauthDb.collection("staffRoles").get());
  await assertFails(unauthDb.collection("userAlerts").get());
  await assertFails(unauthDb.collection("directThreads").get());
  await assertFails(unauthDb.collection("roomTransfers").doc(ALICE_UID).get());

  // Unauthenticated / trial users CANNOT write anywhere (e.g., buy points or create rooms)
  await assertFails(unauthDb.collection("rooms").doc("trial_room").set(validRoomPayload("trial_1")));
  await assertFails(unauthDb.collection("pointTransactions").doc("tx_trial").set({ type: "BUY_POINTS" }));

  // Unauthenticated / trial users CAN read login banner config, public rooms, and unlocked posts
  await assertSucceeds(unauthDb.collection("appConfig").doc("global").get());
  await assertSucceeds(unauthDb.collection("rooms").where("visibility", "==", "PUBLIC").get());
  await assertSucceeds(unauthDb.collection("profilePosts").where("locked", "==", false).get());
});

// 2. USER PROFILES, ADMIN EDIT USER, AND USER CAPABILITY RESTRICTIONS
test("Admin edits users and disables createRoom, enterRoom, mic, video; regular users cannot bypass", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Regular user cannot self-enable invisible mode or lift restrictions
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      invisibleModePreferred: true,
      updatedAt: pastDate(1000),
    })
  );

  // Admin can list users, edit Bob's displayName, and disable room creation / mic / video
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      displayName: "Bob Edited",
      disableCreateRoom: true,
      disableMic: true,
      disableVideo: true,
      updatedAt: pastDate(1000),
    })
  );

  // Bob CANNOT lift his own disableCreateRoom restriction
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      disableCreateRoom: false,
      updatedAt: pastDate(500),
    })
  );

  // Bob CANNOT create a room while disableCreateRoom is true
  await assertFails(
    bobDb.collection("rooms").doc("bob_room").set(validRoomPayload(BOB_UID))
  );

  // Alice creates a room; Bob joins with mic/cam off (succeeds), then tries to enable mic or video (fails!)
  await assertSucceeds(aliceDb.collection("rooms").doc("room_res").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_res").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_res", { micActive: false, camActive: false })
    )
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_res").collection("participants").doc(BOB_UID).update({
      micActive: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_res").collection("participants").doc(BOB_UID).update({
      camActive: true,
      updatedAt: pastDate(500),
    })
  );

  // Admin disables enterRoom for Bob -> Bob cannot join another room
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      disableEnterRoom: true,
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(aliceDb.collection("rooms").doc("room_res_2").set(validRoomPayload(ALICE_UID)));
  await assertFails(
    bobDb.collection("rooms").doc("room_res_2").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_res_2")
    )
  );
});

// 3. SUPPORT STAFF PERMISSIONS: TIMED SUSPENSION, CONDITIONAL INVISIBLE ENTRY, NO BAN/RENAME/CLOSE
test("Support staff can bounce/kick/mute/suspend and enter invisible ONLY when Admin allows it", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();

  const ts = pastDate(4000);

  // Spoofed unverified admin email rejected
  await assertFails(
    spoofedAdminDb.collection("staffInvites").doc("inv_bad").set({
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: true,
      addedByUid: "spoof_uid",
      addedByName: "Spoofer",
      createdAt: ts,
    })
  );

  // Admin adds Support WITHOUT invisible permission initially
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: false,
      addedByUid: ADMIN_UID,
      addedByName: "Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("rooms").doc("room_sup").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_sup").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_sup")
    )
  );

  // Support CANNOT enter invisible while canEnterInvisible is false
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").collection("participants").doc(SUPPORT_UID).set(
      validParticipantPayload(SUPPORT_UID, "room_sup", {
        role: "LISTENER",
        staffTier: "SUPPORT",
        invisible: true,
      })
    )
  );

  // Admin grants canEnterInvisible = true to Support -> NOW Support enters invisibly!
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).update({
      canEnterInvisible: true,
      updatedAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_sup").collection("participants").doc(SUPPORT_UID).set(
      validParticipantPayload(SUPPORT_UID, "room_sup", {
        role: "LISTENER",
        staffTier: "SUPPORT",
        invisible: true,
      })
    )
  );

  // Support CAN mute, bounce/kick Bob, and suspend Bob for 15 minutes
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_sup").update({
      mutedUserIds: [BOB_UID],
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_sup").collection("participants").doc(BOB_UID).delete()
  );
  await assertSucceeds(
    supportDb.collection("users").doc(BOB_UID).update({
      suspendedUntilMillis: Date.now() + 15 * 60 * 1000,
      suspensionReason: "15m cooldown by Support",
      updatedAt: pastDate(900),
    })
  );

  // Suspended Bob CANNOT re-enter room or send messages
  await assertFails(
    bobDb.collection("rooms").doc("room_sup").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_sup")
    )
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_sup").collection("messages").doc("msg_susp").set(
      validMessagePayload("room_sup", BOB_UID)
    )
  );

  // Support CANNOT rename room, close room, or ban user in Alice's room (Admin-only)
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").update({
      title: "Support Renamed",
      updatedAt: pastDate(800),
    })
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").update({
      status: "CLOSED",
      updatedAt: pastDate(800),
    })
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").update({
      bannedUserIds: [BOB_UID],
      updatedAt: pastDate(800),
    })
  );
});

// 4. ADMIN RENAMES ROOMS, BANS USERS, CLOSES ROOMS, AND ENTERS LOCKED ROOMS INVISIBLY
test("Admin renames room, bans user, closes room, and enters locked room invisibly", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_admin").set(
      validRoomPayload(ALICE_UID, { roomLocked: true })
    )
  );

  // Admin enters locked room invisibly
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_admin").collection("participants").doc(ADMIN_UID).set(
      validParticipantPayload(ADMIN_UID, "room_admin", {
        role: "LISTENER",
        staffTier: "ADMIN",
        invisible: true,
      })
    )
  );

  // Admin edits room name & bans Bob
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_admin").update({
      title: "Renamed By Administrator",
      bannedUserIds: [BOB_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Admin closes room
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_admin").update({
      status: "CLOSED",
      updatedAt: pastDate(500),
    })
  );

  // Nobody can send messages into closed room
  await assertFails(
    aliceDb.collection("rooms").doc("room_admin").collection("messages").doc("msg_closed").set(
      validMessagePayload("room_admin", ALICE_UID)
    )
  );
});

// 5. USER ALERTS — SEND, ADMIN EDIT, ADMIN DELETE, RECIPIENT ACKNOWLEDGE
test("UserAlerts: Admin sends, edits, and deletes alerts; recipient acknowledges; regular user blocked", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const ts = pastDate(3000);
  const alertPayload = {
    targetUserId: BOB_UID,
    targetName: "Bob",
    senderId: ADMIN_UID,
    senderName: "Administrator",
    senderTier: "ADMIN",
    roomId: "room_1",
    title: "Official Conduct Warning",
    message: "Please keep voice chat respectful.",
    severity: "WARNING",
    acknowledged: false,
    createdAt: ts,
    updatedAt: ts,
  };

  // Regular user cannot send alerts
  await assertFails(
    aliceDb.collection("userAlerts").doc("alert_bad").set({
      ...alertPayload,
      senderId: ALICE_UID,
      senderName: "Alice",
    })
  );

  // Admin sends alert, then EDITS alert title/message/severity
  await assertSucceeds(adminDb.collection("userAlerts").doc("alert_1").set(alertPayload));
  await assertSucceeds(
    adminDb.collection("userAlerts").doc("alert_1").update({
      title: "Final Urgent Notice",
      message: "Updated warning from Admin.",
      severity: "URGENT",
      updatedAt: pastDate(1000),
    })
  );

  // Bob reads and acknowledges, cannot edit alert text
  await assertSucceeds(bobDb.collection("userAlerts").where("targetUserId", "==", BOB_UID).get());
  await assertFails(
    bobDb.collection("userAlerts").doc("alert_1").update({
      message: "Tampered",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    bobDb.collection("userAlerts").doc("alert_1").update({
      acknowledged: true,
      updatedAt: pastDate(500),
    })
  );

  // Admin can delete alert
  await assertSucceeds(adminDb.collection("userAlerts").doc("alert_1").delete());
});

// 6. ROOM ADMIN, CO-ADMIN, EXCLUSIVE APP ADMIN MIC PRIORITY & INVISIBLE MIC RULES
test("Room Admin & Co-Admin permissions, exclusive Owner Admin mic override, and Support visible-only mic rule", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();
  const guestDb = testEnv.authenticatedContext("guest_user_999").firestore();

  const ts = pastDate(4000);
  // Grant Support with canEnterInvisible = true
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: true,
      addedByUid: ADMIN_UID,
      addedByName: "Owner Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice creates room -> Alice is Room Admin; Alice adds Bob as Co-Admin, clears & edits room name
  await assertSucceeds(aliceDb.collection("rooms").doc("room_hier").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").update({
      moderatorIds: [ALICE_UID, BOB_UID],
      title: "",
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").update({
      title: "Alice's New Room Name",
      updatedAt: pastDate(1800),
    })
  );

  // Guest joins room; Co-Admin Bob CAN mute and kick Guest, but CANNOT ban Guest or rename room!
  await assertSucceeds(
    guestDb.collection("rooms").doc("room_hier").collection("participants").doc("guest_user_999").set(
      validParticipantPayload("guest_user_999", "room_hier")
    )
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_hier").update({
      mutedUserIds: ["guest_user_999"],
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_hier").update({
      bannedUserIds: ["guest_user_999"],
      updatedAt: pastDate(1400),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_hier").update({
      title: "Co-Admin Rename Blocked",
      updatedAt: pastDate(1400),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_hier").collection("participants").doc("guest_user_999").delete()
  );

  // Support enters invisible -> CANNOT turn on mic while invisible; CAN turn on mic once visible!
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).set(
      validParticipantPayload(SUPPORT_UID, "room_hier", {
        role: "LISTENER",
        staffTier: "SUPPORT",
        invisible: true,
        micActive: false,
      })
    )
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).update({
      micActive: true,
      updatedAt: pastDate(1200),
    })
  );
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).update({
      invisible: false,
      micActive: true,
      updatedAt: pastDate(1100),
    })
  );

  // Owner App Admin enters INVISIBLY and takes EXCLUSIVE mic control (micActive = true while invisible = true!)
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_hier").collection("participants").doc(ADMIN_UID).set(
      validParticipantPayload(ADMIN_UID, "room_hier", {
        role: "LISTENER",
        staffTier: "ADMIN",
        invisible: true,
        micActive: true,
      })
    )
  );
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_hier").update({
      adminMicPriorityActive: true,
      updatedAt: pastDate(900),
    })
  );

  // While Owner Admin is on the mic, NOBODY ELSE (not even Room Admin Alice or Support) can enable their mic!
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").collection("participants").doc(ALICE_UID).set(
      validParticipantPayload(ALICE_UID, "room_hier", { role: "HOST", micActive: false })
    )
  );
  await assertFails(
    aliceDb.collection("rooms").doc("room_hier").collection("participants").doc(ALICE_UID).update({
      micActive: true,
      updatedAt: pastDate(700),
    })
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).update({
      micActive: true,
      updatedAt: pastDate(700),
    })
  );

  // When Owner Admin releases mic priority, Alice can turn on her mic again!
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_hier").update({
      adminMicPriorityActive: false,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").collection("participants").doc(ALICE_UID).update({
      micActive: true,
      updatedAt: pastDate(300),
    })
  );
});

// 7. USER DIRECTORY & FRIENDSHIPS (Send, Accept, Cancel, Unfriend, Cross-user Denied)
test("User directory and friendships enforce zero-trust request/accept/remove workflow", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  const ts = pastDate(3000);

  // Alice and Bob publish minimal public directory cards
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      avatarColorHex: "#1877F2",
      staffTier: "USER",
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("userDirectory").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      handle: "bob_vox",
      avatarColorHex: "#0084FF",
      staffTier: "USER",
      updatedAt: ts,
    })
  );

  // Regular user cannot claim ADMIN in userDirectory
  await assertFails(
    bobDb.collection("userDirectory").doc(BOB_UID).update({
      staffTier: "ADMIN",
      updatedAt: pastDate(2000),
    })
  );

  // Any signed-in user can search userDirectory
  await assertSucceeds(charlieDb.collection("userDirectory").get());

  const friendshipId = `${ALICE_UID}_${BOB_UID}`;
  const reqPayload = {
    userIds: [ALICE_UID, BOB_UID],
    requesterId: ALICE_UID,
    requesterName: "Alice",
    requesterHandle: "alice_vox",
    requesterColorHex: "#1877F2",
    requesterTier: "USER",
    recipientId: BOB_UID,
    recipientName: "Bob",
    recipientHandle: "bob_vox",
    recipientColorHex: "#0084FF",
    recipientTier: "USER",
    status: "PENDING",
    createdAt: ts,
    updatedAt: ts,
  };

  // Charlie cannot forge a friend request on Alice's behalf
  await assertFails(charlieDb.collection("friendships").doc(friendshipId).set(reqPayload));

  // Alice sends friend request to Bob
  await assertSucceeds(aliceDb.collection("friendships").doc(friendshipId).set(reqPayload));

  // Alice CANNOT accept her own outgoing request
  await assertFails(
    aliceDb.collection("friendships").doc(friendshipId).update({
      status: "ACCEPTED",
      updatedAt: pastDate(1500),
    })
  );

  // Charlie cannot read Alice-Bob friendship
  await assertFails(charlieDb.collection("friendships").doc(friendshipId).get());

  // Bob lists his friendships and accepts Alice's request
  await assertSucceeds(
    bobDb.collection("friendships").where("userIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("friendships").doc(friendshipId).update({
      status: "ACCEPTED",
      updatedAt: pastDate(1000),
    })
  );

  // Either friend can unfriend (delete), Charlie cannot
  await assertFails(charlieDb.collection("friendships").doc(friendshipId).delete());
  await assertSucceeds(aliceDb.collection("friendships").doc(friendshipId).delete());
});

// 8. PRIVATE DIRECT MESSAGES (Threads & Messages Strictly Scoped to Participants)
test("Private direct threads and messages are strictly scoped to the two participants", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  const ts = pastDate(3000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  const threadPayload = {
    participantIds: [ALICE_UID, BOB_UID],
    userAId: ALICE_UID,
    userAName: "Alice",
    userAColorHex: "#1877F2",
    userATier: "USER",
    userBId: BOB_UID,
    userBName: "Bob",
    userBColorHex: "#0084FF",
    userBTier: "USER",
    lastMessageText: "",
    lastMessageSenderId: "",
    unreadByUserIds: [],
    createdAt: ts,
    updatedAt: ts,
  };

  // Charlie cannot create a thread between Alice and Bob
  await assertFails(charlieDb.collection("directThreads").doc(threadId).set(threadPayload));

  // Alice creates direct thread with Bob
  await assertSucceeds(aliceDb.collection("directThreads").doc(threadId).set(threadPayload));

  // Alice sends a private message to Bob and updates thread preview
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_1").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      senderTier: "USER",
      text: "Hey Bob! Hop into the lounge?",
      createdAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      lastMessageText: "Hey Bob! Hop into the lounge?",
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [BOB_UID],
      updatedAt: pastDate(1900),
    })
  );

  // Bob lists his threads and reads messages using single-field array-contains query
  await assertSucceeds(
    bobDb.collection("directThreads").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb
      .collection("directThreads")
      .doc(threadId)
      .collection("messages")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );

  // Bob marks thread as read
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).update({
      unreadByUserIds: [],
      updatedAt: pastDate(1000),
    })
  );

  // Charlie CANNOT read thread, CANNOT list/read messages, CANNOT send message into Alice-Bob thread
  await assertFails(charlieDb.collection("directThreads").doc(threadId).get());
  await assertFails(
    charlieDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_1").get()
  );
  await assertFails(
    charlieDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_spoof").set({
      threadId,
      participantIds: [ALICE_UID, "charlie_999"],
      senderId: "charlie_999",
      senderName: "Charlie",
      senderTier: "USER",
      text: "Intruder!",
      createdAt: pastDate(800),
    })
  );
});

// 9. PROMOTING OTHER USERS TO APP ADMIN (Direct Promotion + Email Admin Invite)
test("Owner Admin can make other users App Admins directly or via invite, granting full Admin console access", async () => {
  const ownerDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice.admin@voxstage.test", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob.guest@voxstage.test", email_verified: true })
    .firestore();
  const ts = pastDate(3000);

  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));

  // Before promotion, Alice cannot list all users or promote herself
  await assertFails(aliceDb.collection("users").get());
  await assertFails(
    aliceDb.collection("staffRoles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice.admin@voxstage.test",
      displayName: "Alice",
      staffTier: "ADMIN",
      canEnterInvisible: true,
      addedByUid: ALICE_UID,
      addedByName: "Alice",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner invites Alice as an App Admin via email invite
  await assertSucceeds(
    ownerDb.collection("staffInvites").doc("inv_alice_admin").set({
      email: "alice.admin@voxstage.test",
      displayName: "Alice Co-Owner",
      staffTier: "ADMIN",
      canEnterInvisible: true,
      addedByUid: ADMIN_UID,
      addedByName: "Owner Admin",
      createdAt: ts,
    })
  );

  // Alice claims her App Admin role on sign-in
  await assertSucceeds(
    aliceDb.collection("staffRoles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice.admin@voxstage.test",
      displayName: "Alice Co-Owner",
      staffTier: "ADMIN",
      canEnterInvisible: true,
      addedByUid: ADMIN_UID,
      addedByName: "Owner Admin",
      inviteId: "inv_alice_admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Now promoted App Admin Alice CAN list all users, list staffRoles, and edit Bob's profile!
  await assertSucceeds(aliceDb.collection("users").get());
  await assertSucceeds(aliceDb.collection("staffRoles").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(BOB_UID).update({
      disableMic: true,
      updatedAt: pastDate(1000),
    })
  );

  // Owner Admin can also directly promote Bob to App Admin and later remove Alice
  await assertSucceeds(
    ownerDb.collection("staffRoles").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob.guest@voxstage.test",
      displayName: "Bob Admin",
      staffTier: "ADMIN",
      canEnterInvisible: true,
      addedByUid: ADMIN_UID,
      addedByName: "Owner Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(ownerDb.collection("staffRoles").doc(ALICE_UID).delete());
});

// 10. PROFILE PICTURES & PROFILE POSTS (Text/Photo updates, Likes, Discovery viewing, Moderation)
test("Users can upload profile pictures, publish text/photo posts, browse profiles, like, and delete own posts", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  const ts = pastDate(3000);
  const sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

  // Alice sets her profile picture in users and userDirectory
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoBase64: sampleBase64,
      updatedAt: pastDate(2500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      bio: "Sharing live stage updates!",
      avatarColorHex: "#0088FF",
      photoBase64: sampleBase64,
      staffTier: "USER",
      postCount: 0,
      updatedAt: pastDate(2400),
    })
  );

  // Empty post (neither text nor image) MUST be denied
  await assertFails(
    aliceDb.collection("profilePosts").doc("post_empty").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: sampleBase64,
      authorTier: "USER",
      text: "",
      imageBase64: "",
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice publishes a photo + text update on her profile
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_alice_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: sampleBase64,
      authorTier: "USER",
      text: "Going live tonight at 8pm!",
      imageBase64: sampleBase64,
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob CANNOT spoof a post under Alice's authorId
  await assertFails(
    bobDb.collection("profilePosts").doc("post_spoof").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorTier: "USER",
      text: "Impersonated post",
      imageBase64: "",
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob browses Alice's profile & views Alice's posts
  await assertSucceeds(bobDb.collection("userDirectory").doc(ALICE_UID).get());
  await assertSucceeds(
    bobDb.collection("profilePosts").where("authorId", "==", ALICE_UID).get()
  );

  // Bob likes Alice's post (succeeds); Bob CANNOT edit Alice's post text or forge Charlie's like
  await assertSucceeds(
    bobDb.collection("profilePosts").doc("post_alice_1").update({
      likedByUserIds: [BOB_UID],
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    bobDb.collection("profilePosts").doc("post_alice_1").update({
      text: "Hacked caption",
      updatedAt: pastDate(1400),
    })
  );
  await assertFails(
    bobDb.collection("profilePosts").doc("post_alice_1").update({
      likedByUserIds: [BOB_UID, "charlie_999"],
      updatedAt: pastDate(1400),
    })
  );

  // Charlie CANNOT delete Alice's post, while Admin CAN moderate/delete it
  await assertFails(charlieDb.collection("profilePosts").doc("post_alice_1").delete());
  await assertSucceeds(adminDb.collection("profilePosts").doc("post_alice_1").delete());
});

// 11. 24-HOUR ROOM BOUNCE LOCKOUT (Admin & Support bounce blocks re-entry for 24 hours)
test("Admin or Support bouncing a user locks them out of entering the room for 24 hours", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: false,
      addedByUid: ADMIN_UID,
      addedByName: "Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("rooms").doc("room_bounce").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_bounce").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_bounce")
    )
  );

  // Support bounces Bob for 24 hours (deletes participant + writes bouncedUntilByUserId[BOB_UID])
  const lockoutUntilMs = Date.now() + 24 * 60 * 60 * 1000;
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_bounce").collection("participants").doc(BOB_UID).delete()
  );
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_bounce").update({
      bouncedUntilByUserId: { [BOB_UID]: lockoutUntilMs },
      updatedAt: pastDate(1000),
    })
  );

  // Bob CANNOT re-enter the room or send messages during the 24-hour bounce window!
  await assertFails(
    bobDb.collection("rooms").doc("room_bounce").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_bounce")
    )
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_bounce").collection("messages").doc("msg_bounced").set(
      validMessagePayload("room_bounce", BOB_UID)
    )
  );

  // Once the 24-hour lockout has expired (timestamp in past), Bob CAN re-enter!
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_bounce").update({
      bouncedUntilByUserId: { [BOB_UID]: Date.now() - 60_000 },
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_bounce").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_bounce")
    )
  );
});

// 12. PRIVATE CHAT SNAP PICTURES (imageBase64 allowed with or without caption)
test("Private chat supports sending snap pictures with or without text", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(3000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;
  const sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Photo-only snap message (empty text, non-empty imageBase64) succeeds
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_snap_1").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      senderTier: "USER",
      text: "",
      imageBase64: sampleBase64,
      createdAt: pastDate(1500),
    })
  );

  // Empty text AND empty imageBase64 is rejected
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_empty").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      senderTier: "USER",
      text: "",
      imageBase64: "",
      createdAt: pastDate(1200),
    })
  );
});

// 13. LOCKED PROFILE PHOTOS, POINTS WALLET, $1/$1 REVENUE SPLIT & $15/MO CREATOR SUBSCRIPTION
test("Locked photos, 2-point ($2) unlock with $1/$1 split, and $15/mo creator subscriptions enforce strict ledger math", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  const ts = pastDate(4000);
  const sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      adultVerified: true,
      birthYear: 1995,
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      ...validProfilePayload(BOB_UID),
      adultVerified: true,
      birthYear: 1992,
    })
  );

  // Alice posts a locked photo (2 points)
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_locked_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "VIP backstage photo",
      imageBase64: sampleBase64,
      locked: true,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Locking a text-only post (no photo) is rejected
  await assertFails(
    aliceDb.collection("profilePosts").doc("post_locked_no_img").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorTier: "USER",
      text: "Text only",
      imageBase64: "",
      locked: true,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob buys 2 points for $2 (pointsBalance 0 -> 2, totalSpentCents 0 -> 200)
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      pointsBalance: 2,
      totalSpentCents: 200,
      updatedAt: pastDate(3000),
    })
  );
  await assertSucceeds(
    bobDb.collection("pointTransactions").doc("tx_buy_1").set({
      type: "BUY_POINTS",
      buyerId: BOB_UID,
      buyerName: "Bob",
      creatorId: "",
      creatorName: "",
      postId: "",
      pointsDelta: 2,
      grossCents: 200,
      creatorShareCents: 0,
      platformShareCents: 0,
      createdAt: pastDate(2900),
    })
  );

  // Bob CANNOT give himself free points without matching totalSpentCents or self-credit creatorEarningsCents
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      pointsBalance: 100,
      totalSpentCents: 200,
      updatedAt: pastDate(2800),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      creatorEarningsCents: 5000,
      updatedAt: pastDate(2800),
    })
  );

  // Bob unlocks Alice's locked photo: spends 2 pts, adds self to unlockedByUserIds, credits Alice +1 usable point (pointsBalance + creatorEarnedPoints)!
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      pointsBalance: 0,
      updatedAt: pastDate(2500),
    })
  );
  await assertSucceeds(
    bobDb.collection("profilePosts").doc("post_locked_1").update({
      unlockedByUserIds: [BOB_UID],
      updatedAt: pastDate(2400),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 1,
      creatorEarnedPoints: 1,
      updatedAt: pastDate(2300),
    })
  );
  await assertSucceeds(
    bobDb.collection("pointTransactions").doc("tx_unlock_1").set({
      type: "UNLOCK_PHOTO",
      buyerId: BOB_UID,
      buyerName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      postId: "post_locked_1",
      pointsDelta: -2,
      grossCents: 200,
      creatorShareCents: 0,
      platformShareCents: 200,
      createdAt: pastDate(2200),
    })
  );

  // Forged split (e.g., $2 creator / $0 app owner) is REJECTED by rules!
  await assertFails(
    bobDb.collection("pointTransactions").doc("tx_unlock_forged").set({
      type: "UNLOCK_PHOTO",
      buyerId: BOB_UID,
      buyerName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      postId: "post_locked_1",
      pointsDelta: -2,
      grossCents: 200,
      creatorShareCents: 200,
      platformShareCents: 0,
      createdAt: pastDate(2100),
    })
  );

  // Bob subscribes to Alice for $15/month (30 days) — 100% revenue to app owner, Alice earns +8 usable points
  const subId = `${BOB_UID}_${ALICE_UID}`;
  await assertSucceeds(
    bobDb.collection("creatorSubscriptions").doc(subId).set({
      subscriberId: BOB_UID,
      creatorId: ALICE_UID,
      creatorName: "Alice",
      priceCents: 1500,
      creatorShareCents: 0,
      platformShareCents: 1500,
      active: true,
      expiresAtMillis: Date.now() + 30 * 24 * 60 * 60 * 1000,
      createdAt: pastDate(1800),
      updatedAt: pastDate(1800),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 9,
      creatorEarnedPoints: 9,
      updatedAt: pastDate(1700),
    })
  );
  await assertSucceeds(
    bobDb.collection("pointTransactions").doc("tx_sub_1").set({
      type: "SUBSCRIBE_CREATOR",
      buyerId: BOB_UID,
      buyerName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      postId: "",
      pointsDelta: 0,
      grossCents: 1500,
      creatorShareCents: 0,
      platformShareCents: 1500,
      createdAt: pastDate(1600),
    })
  );

  // Charlie cannot read Bob's subscription or transaction; Admin CAN read all ledger entries
  await assertFails(charlieDb.collection("creatorSubscriptions").doc(subId).get());
  await assertFails(charlieDb.collection("pointTransactions").doc("tx_unlock_1").get());
  await assertSucceeds(adminDb.collection("pointTransactions").get());
});

// 14. 5-POINT ($5) BOOST POST TO TOP OF DISCOVERY (Atomic Wallet Deduction + Ledger Enforcement)
test("Authors can pay 5 points ($5) to boost their post to the top of Discovery; free or unauthorized boosts are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));

  // Alice publishes a regular (unboosted) post
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_boost_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Check out my live show tonight!",
      imageBase64: "",
      boostedAtMillis: 0,
      boostedUntilMillis: 0,
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const nowMs = Date.now();
  const boostUntilMs = nowMs + 24 * 60 * 60 * 1000;

  // Alice has 0 points: trying to boost WITHOUT paying 5 points is DENIED
  await assertFails(
    aliceDb.collection("profilePosts").doc("post_boost_1").update({
      boostedAtMillis: nowMs,
      boostedUntilMillis: boostUntilMs,
      updatedAt: pastDate(2000),
    })
  );

  // Alice buys 10 points ($10)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 10,
      totalSpentCents: 1000,
      updatedAt: pastDate(1900),
    })
  );

  // Even with 10 points, Alice CANNOT boost without actually deducting 5 points in the same atomic batch!
  await assertFails(
    aliceDb.collection("profilePosts").doc("post_boost_1").update({
      boostedAtMillis: nowMs,
      boostedUntilMillis: boostUntilMs,
      updatedAt: pastDate(1800),
    })
  );

  // Alice atomically pays 5 points (10 -> 5), boosts post_boost_1 for 24h, and records BOOST_POST ledger entry
  const boostBatch = aliceDb.batch();
  boostBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 5,
    updatedAt: pastDate(1500),
  });
  boostBatch.update(aliceDb.collection("profilePosts").doc("post_boost_1"), {
    boostedAtMillis: nowMs,
    boostedUntilMillis: boostUntilMs,
    updatedAt: pastDate(1500),
  });
  boostBatch.set(aliceDb.collection("pointTransactions").doc("tx_boost_1"), {
    type: "BOOST_POST",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "post_boost_1",
    pointsDelta: -5,
    grossCents: 500,
    creatorShareCents: 0,
    platformShareCents: 500,
    createdAt: pastDate(1500),
  });
  await assertSucceeds(boostBatch.commit());

  // Alice uses her remaining 5 points (5 -> 0) to publish a NEW post already boosted in one atomic batch
  const createBoostBatch = aliceDb.batch();
  createBoostBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 0,
    updatedAt: pastDate(1200),
  });
  createBoostBatch.set(aliceDb.collection("profilePosts").doc("post_boost_2"), {
    authorId: ALICE_UID,
    authorName: "Alice",
    authorHandle: "alice_vox",
    authorColorHex: "#0088FF",
    authorPhotoBase64: "",
    authorTier: "USER",
    text: "Featured announcement right at the top of Discovery!",
    imageBase64: "",
    boostedAtMillis: nowMs + 1000,
    boostedUntilMillis: boostUntilMs,
    likedByUserIds: [],
    createdAt: pastDate(1200),
    updatedAt: pastDate(1200),
  });
  createBoostBatch.set(aliceDb.collection("pointTransactions").doc("tx_boost_2"), {
    type: "BOOST_POST",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "post_boost_2",
    pointsDelta: -5,
    grossCents: 500,
    creatorShareCents: 0,
    platformShareCents: 500,
    createdAt: pastDate(1200),
  });
  await assertSucceeds(createBoostBatch.commit());

  // Bob CANNOT boost Alice's post even if Bob buys 5 points and deducts them
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      pointsBalance: 5,
      totalSpentCents: 500,
      updatedAt: pastDate(1100),
    })
  );
  const bobStealBoost = bobDb.batch();
  bobStealBoost.update(bobDb.collection("users").doc(BOB_UID), {
    pointsBalance: 0,
    updatedAt: pastDate(1000),
  });
  bobStealBoost.update(bobDb.collection("profilePosts").doc("post_boost_1"), {
    boostedAtMillis: nowMs + 2000,
    boostedUntilMillis: boostUntilMs,
    updatedAt: pastDate(1000),
  });
  await assertFails(bobStealBoost.commit());

  // Forged BOOST_POST transaction (e.g. $0 platform share or -1 pt) is DENIED
  await assertFails(
    aliceDb.collection("pointTransactions").doc("tx_boost_forged").set({
      type: "BOOST_POST",
      buyerId: ALICE_UID,
      buyerName: "Alice",
      creatorId: "",
      creatorName: "",
      postId: "post_boost_1",
      pointsDelta: -1,
      grossCents: 500,
      creatorShareCents: 0,
      platformShareCents: 0,
      createdAt: pastDate(900),
    })
  );
});

// 15. COMMENTS ON POSTS & SHARING UNLOCKED POSTS (LOCKED PICTURES CANNOT BE SHARED)
test("Users can comment on posts and share unlocked posts, while locked pictures are strictly non-shareable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  const ts = pastDate(4000);
  const sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

  // Alice verifies 18+ age so she can publish a locked photo post in addition to a public photo post
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      adultVerified: true,
      birthYear: 1994,
    })
  );

  // Alice creates an unlocked photo post AND a locked photo post
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_public_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Open studio sunset!",
      imageBase64: sampleBase64,
      locked: false,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      commentCount: 0,
      shareCount: 0,
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_secret_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "VIP locked photo",
      imageBase64: sampleBase64,
      locked: true,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      commentCount: 0,
      shareCount: 0,
      likedByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob comments on Alice's public post and increments commentCount
  const commentBatch = bobDb.batch();
  commentBatch.set(
    bobDb.collection("profilePosts").doc("post_public_1").collection("comments").doc("c_bob_1"),
    {
      postId: "post_public_1",
      authorId: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Awesome shot Alice!",
      createdAt: pastDate(2500),
    }
  );
  commentBatch.update(bobDb.collection("profilePosts").doc("post_public_1"), {
    commentCount: 1,
    updatedAt: pastDate(2500),
  });
  await assertSucceeds(commentBatch.commit());

  // Charlie CANNOT delete Bob's comment; Alice (post owner) CAN delete it
  await assertFails(
    charlieDb.collection("profilePosts").doc("post_public_1").collection("comments").doc("c_bob_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_public_1").collection("comments").doc("c_bob_1").delete()
  );

  // Bob shares Alice's UNLOCKED post to his profile + increments shareCount (SUCCEEDS)
  const shareBatch = bobDb.batch();
  shareBatch.set(bobDb.collection("profilePosts").doc("post_shared_ok"), {
    authorId: BOB_UID,
    authorName: "Bob",
    authorHandle: "bob_vox",
    authorColorHex: "#0088FF",
    authorPhotoBase64: "",
    authorTier: "USER",
    text: "Check out Alice's sunset! Open studio sunset!",
    imageBase64: sampleBase64,
    locked: false,
    unlockPricePoints: 2,
    unlockedByUserIds: [],
    sharedFromPostId: "post_public_1",
    sharedFromAuthorId: ALICE_UID,
    sharedFromAuthorName: "Alice",
    sharedFromAuthorHandle: "alice_vox",
    commentCount: 0,
    shareCount: 0,
    likedByUserIds: [],
    createdAt: pastDate(2000),
    updatedAt: pastDate(2000),
  });
  shareBatch.update(bobDb.collection("profilePosts").doc("post_public_1"), {
    shareCount: 1,
    updatedAt: pastDate(2000),
  });
  await assertSucceeds(shareBatch.commit());

  // Bob CANNOT share/repost Alice's LOCKED picture or increment shareCount on a locked picture!
  await assertFails(
    bobDb.collection("profilePosts").doc("post_shared_locked_fail").set({
      authorId: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Trying to leak locked photo",
      imageBase64: sampleBase64,
      locked: false,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      sharedFromPostId: "post_secret_1",
      sharedFromAuthorId: ALICE_UID,
      sharedFromAuthorName: "Alice",
      sharedFromAuthorHandle: "alice_vox",
      commentCount: 0,
      shareCount: 0,
      likedByUserIds: [],
      createdAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    bobDb.collection("profilePosts").doc("post_secret_1").update({
      shareCount: 1,
      updatedAt: pastDate(1500),
    })
  );
});

// 16. GROUP CHATS, DM PRIVACY POLICIES (OPEN/PAID/DISABLED), $10 PAID DM ($5 USER / $5 APP OWNER) & KICK PAID MEMBER
test("Group chats, DM privacy controls, $10 paid DM ($5/$5 split), and kicking paid members are enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieUid = "charlie_dm_3";
  const daveUid = "dave_dm_4";
  const charlieDb = testEnv.authenticatedContext(charlieUid).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertSucceeds(charlieDb.collection("users").doc(charlieUid).set(validProfilePayload(charlieUid)));

  // Publish directory entries
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      bio: "",
      avatarColorHex: "#0088FF",
      photoBase64: "",
      staffTier: "USER",
      postCount: 0,
      dmPolicy: "DISABLED",
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("userDirectory").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      handle: "bob_vox",
      bio: "",
      avatarColorHex: "#0088FF",
      photoBase64: "",
      staffTier: "USER",
      postCount: 0,
      dmPolicy: "OPEN",
      updatedAt: ts,
    })
  );

  // 1) Alice disabled private messages -> Bob CANNOT open a 1-on-1 DM with Alice
  const pairId = `${ALICE_UID}_${BOB_UID}`;
  await assertFails(
    bobDb.collection("directThreads").doc(pairId).set({
      participantIds: [ALICE_UID, BOB_UID],
      isGroup: false,
      groupName: "",
      participantNamesById: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      kickedUserIds: [],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 2) Alice switches to PAID ($10 locked private messages)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dmPolicy: "PAID",
      updatedAt: pastDate(4500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).update({
      dmPolicy: "PAID",
      updatedAt: pastDate(4500),
    })
  );

  // Unpaid Bob STILL CANNOT start a 1-on-1 DM with Alice
  await assertFails(
    bobDb.collection("directThreads").doc(pairId).set({
      participantIds: [ALICE_UID, BOB_UID],
      isGroup: false,
      groupName: "",
      participantNamesById: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      kickedUserIds: [],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      createdAt: pastDate(4000),
      updatedAt: pastDate(4000),
    })
  );

  // 3) Bob pays $5 (100% to app owner) -> creates dmAccessPasses/{bob}_{alice} + credits Alice +1 usable point + UNLOCK_DM ledger
  const passId = `${BOB_UID}_${ALICE_UID}`;
  const payBatch = bobDb.batch();
  payBatch.set(bobDb.collection("dmAccessPasses").doc(passId), {
    senderId: BOB_UID,
    senderName: "Bob",
    recipientId: ALICE_UID,
    recipientName: "Alice",
    priceCents: 500,
    creatorShareCents: 0,
    platformShareCents: 500,
    active: true,
    kicked: false,
    createdAt: pastDate(3500),
    updatedAt: pastDate(3500),
  });
  payBatch.update(bobDb.collection("users").doc(BOB_UID), {
    totalSpentCents: 500,
    updatedAt: pastDate(3500),
  });
  payBatch.update(bobDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 1,
    creatorEarnedPoints: 1,
    updatedAt: pastDate(3500),
  });
  payBatch.set(bobDb.collection("pointTransactions").doc("tx_dm_unlock_1"), {
    type: "UNLOCK_DM",
    buyerId: BOB_UID,
    buyerName: "Bob",
    creatorId: ALICE_UID,
    creatorName: "Alice",
    postId: "",
    pointsDelta: 0,
    grossCents: 500,
    creatorShareCents: 0,
    platformShareCents: 500,
    createdAt: pastDate(3500),
  });
  await assertSucceeds(payBatch.commit());

  // Now Bob CAN open the 1-on-1 DM with Alice and send a private message!
  await assertSucceeds(
    bobDb.collection("directThreads").doc(pairId).set({
      participantIds: [ALICE_UID, BOB_UID],
      isGroup: false,
      groupName: "",
      participantNamesById: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      kickedUserIds: [],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      createdAt: pastDate(3000),
      updatedAt: pastDate(3000),
    })
  );
  await assertSucceeds(
    bobDb.collection("directThreads").doc(pairId).collection("messages").doc("dm_paid_1").set({
      threadId: pairId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Hey Alice, thanks for unlocking DMs!",
      createdAt: pastDate(2800),
    })
  );

  // 4) Alice kicks paid member Bob -> Bob is immediately blocked from sending further messages!
  await assertSucceeds(
    aliceDb.collection("dmAccessPasses").doc(passId).update({
      active: false,
      kicked: true,
      updatedAt: pastDate(2500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(pairId).update({
      kickedUserIds: [BOB_UID],
      lastMessageText: "Bob was removed from messaging Alice",
      lastMessageSenderId: ALICE_UID,
      updatedAt: pastDate(2500),
    })
  );
  await assertFails(
    bobDb.collection("directThreads").doc(pairId).collection("messages").doc("dm_paid_after_kick").set({
      threadId: pairId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Can I still message?",
      createdAt: pastDate(2200),
    })
  );

  // 5) Group Chat: Bob creates a group chat with Alice + Charlie, adds Dave, and Charlie sends a message
  await assertSucceeds(
    bobDb.collection("directThreads").doc("group_chat_1").set({
      participantIds: [BOB_UID, ALICE_UID, charlieUid],
      isGroup: true,
      groupName: "Bob, Alice & Charlie",
      participantNamesById: { [BOB_UID]: "Bob", [ALICE_UID]: "Alice", [charlieUid]: "Charlie" },
      kickedUserIds: [],
      userAId: BOB_UID,
      userAName: "Bob",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: ALICE_UID,
      userBName: "Alice",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Bob created a group chat",
      lastMessageSenderId: BOB_UID,
      unreadByUserIds: [ALICE_UID, charlieUid],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    charlieDb.collection("directThreads").doc("group_chat_1").update({
      participantIds: [BOB_UID, ALICE_UID, charlieUid, daveUid],
      participantNamesById: {
        [BOB_UID]: "Bob",
        [ALICE_UID]: "Alice",
        [charlieUid]: "Charlie",
        [daveUid]: "Dave",
      },
      groupName: "Studio Squad",
      lastMessageText: "Charlie added Dave to the group",
      lastMessageSenderId: charlieUid,
      unreadByUserIds: [BOB_UID, ALICE_UID, daveUid],
      updatedAt: pastDate(1800),
    })
  );
  await assertSucceeds(
    charlieDb.collection("directThreads").doc("group_chat_1").collection("messages").doc("grp_msg_1").set({
      threadId: "group_chat_1",
      participantIds: [BOB_UID, ALICE_UID, charlieUid, daveUid],
      senderId: charlieUid,
      senderName: "Charlie",
      senderTier: "USER",
      text: "Welcome to the group chat everyone!",
      createdAt: pastDate(1500),
    })
  );
});

// 17. ROOM MESSAGE EMOJI REACTIONS, PRIVATE VOICE NOTES & 18+ AGE VERIFICATION FOR LOCKED PHOTOS
test("Room message emoji reactions, private voice notes, and 18+ locked photo gating are enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const minorUid = "minor_user_17";
  const minorDb = testEnv.authenticatedContext(minorUid).firestore();
  const ts = pastDate(5000);
  const sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
  const sampleAudioBase64 = "AAAAIGZ0eXBNNEEgAAACAGlzb21pc28y";

  // 1) Room message emoji reactions stored in Firestore
  await assertSucceeds(aliceDb.collection("rooms").doc("room_react").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_react").collection("messages").doc("msg_r1").set(
      validMessagePayload("room_react", ALICE_UID)
    )
  );
  // Bob reacts with ❤️ and 🔥
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_react").collection("messages").doc("msg_r1").update({
      reactionsByEmoji: { "❤️": [BOB_UID], "🔥": [BOB_UID] },
      updatedAt: pastDate(2000),
    })
  );
  // Bob CANNOT edit message text while reacting
  await assertFails(
    bobDb.collection("rooms").doc("room_react").collection("messages").doc("msg_r1").update({
      text: "Tampered text",
      reactionsByEmoji: { "❤️": [BOB_UID] },
      updatedAt: pastDate(1900),
    })
  );
  // Invalid emoji key is rejected
  await assertFails(
    bobDb.collection("rooms").doc("room_react").collection("messages").doc("msg_r1").update({
      reactionsByEmoji: { "INVALID_KEY": [BOB_UID] },
      updatedAt: pastDate(1800),
    })
  );

  // 2) Private chat voice note (audioBase64 + audioDurationSec with empty text)
  const dmThreadId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(dmThreadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(dmThreadId).collection("messages").doc("dm_voice_1").set({
      threadId: dmThreadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      senderTier: "USER",
      text: "",
      audioBase64: sampleAudioBase64,
      audioDurationSec: 7,
      createdAt: pastDate(1500),
    })
  );

  // 3) 18+ age verification enforcement for locking & unlocking photos
  await assertSucceeds(minorDb.collection("users").doc(minorUid).set(validProfilePayload(minorUid)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));

  // Under-18 birthYear CANNOT set adultVerified = true
  await assertFails(
    minorDb.collection("users").doc(minorUid).update({
      adultVerified: true,
      birthYear: 2012,
      updatedAt: pastDate(1400),
    })
  );

  // Unverified user CANNOT create a locked photo post
  await assertFails(
    minorDb.collection("profilePosts").doc("post_minor_locked").set({
      authorId: minorUid,
      authorName: "Minor",
      authorHandle: "minor_u",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Locked photo attempt",
      imageBase64: sampleBase64,
      locked: true,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      likedByUserIds: [],
      createdAt: pastDate(1300),
      updatedAt: pastDate(1300),
    })
  );

  // Alice verifies 18+ age (birthYear 1996) -> CAN create a locked photo
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      adultVerified: true,
      birthYear: 1996,
      updatedAt: pastDate(1200),
    })
  );
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_adult_locked").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "18+ locked studio photo",
      imageBase64: sampleBase64,
      locked: true,
      unlockPricePoints: 2,
      unlockedByUserIds: [],
      likedByUserIds: [],
      createdAt: pastDate(1100),
      updatedAt: pastDate(1100),
    })
  );

  // Unverified user CANNOT unlock Alice's locked photo or subscribe to view locked photos
  await assertFails(
    minorDb.collection("profilePosts").doc("post_adult_locked").update({
      unlockedByUserIds: [minorUid],
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    minorDb.collection("creatorSubscriptions").doc(`${minorUid}_${ALICE_UID}`).set({
      subscriberId: minorUid,
      creatorId: ALICE_UID,
      creatorName: "Alice",
      priceCents: 1500,
      creatorShareCents: 0,
      platformShareCents: 1500,
      active: true,
      expiresAtMillis: Date.now() + 30 * 24 * 60 * 60 * 1000,
      createdAt: pastDate(900),
      updatedAt: pastDate(900),
    })
  );

  // Bob verifies 18+ age -> CAN unlock Alice's locked photo!
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      ...validProfilePayload(BOB_UID),
      adultVerified: true,
      birthYear: 1990,
    })
  );
  await assertSucceeds(
    bobDb.collection("profilePosts").doc("post_adult_locked").update({
      unlockedByUserIds: [BOB_UID],
      updatedAt: pastDate(800),
    })
  );
});

test("Users can update their own online/offline presence in userDirectory; cross-user spoofing is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      bio: "",
      avatarColorHex: "#0088FF",
      photoBase64: "",
      staffTier: "USER",
      postCount: 0,
      dmPolicy: "OPEN",
      online: true,
      lastActiveAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Alice can mark herself offline
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).update({
      online: false,
      lastActiveAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot spoof Alice's online presence
  await assertFails(
    bobDb.collection("userDirectory").doc(ALICE_UID).update({
      online: true,
      lastActiveAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

// 19. CHAT MODERATION: BLOCK CONTACT REMOVES FRIEND AND STOPS INCOMING MESSAGES
test("Blocking a contact in private chat stops incoming messages and allows unblocking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      isGroup: false,
      groupName: "",
      participantNamesById: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      kickedUserIds: [],
      blockedByUserIds: [],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Hey",
      lastMessageSenderId: BOB_UID,
      unreadByUserIds: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice blocks Bob in her profile and on the DM thread
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      blockedUserIds: [BOB_UID],
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      blockedByUserIds: [ALICE_UID],
      unreadByUserIds: [],
      updatedAt: pastDate(1900),
    })
  );

  // Bob CANNOT send incoming messages once blocked
  await assertFails(
    bobDb.collection("directThreads").doc(threadId).collection("messages").doc("msg_blocked").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Trying to message while blocked",
      createdAt: pastDate(1500),
    })
  );
  // Bob CANNOT remove Alice's block on the thread
  await assertFails(
    bobDb.collection("directThreads").doc(threadId).update({
      blockedByUserIds: [],
      updatedAt: pastDate(1400),
    })
  );

  // Alice unblocks Bob -> Bob can message again
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      blockedUserIds: [],
      updatedAt: pastDate(1200),
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      blockedByUserIds: [],
      updatedAt: pastDate(1100),
    })
  );
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).collection("messages").doc("msg_after_unblock").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Thanks for unblocking!",
      createdAt: pastDate(900),
    })
  );
});

// 20. ADMIN APP CONFIG (LOGIN NEWS BANNER & TRIAL LOGIN TOGGLE)
test("Admin manages login screen news banner and trial login toggle; non-admins are read-only", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    adminDb.collection("appConfig").doc("global").set({
      bannerTitle: "VoxStage v2.5 Live!",
      bannerMessage: "Push-to-talk private chats and Google Play point packs are now live.",
      bannerEnabled: true,
      trialLoginsEnabled: false,
      updatedByName: "Admin",
      updatedAt: pastDate(2000),
    })
  );

  // Login screen (unauth) and regular users can read global banner/config
  await assertSucceeds(unauthDb.collection("appConfig").doc("global").get());
  await assertSucceeds(aliceDb.collection("appConfig").doc("global").get());

  // Regular users CANNOT edit appConfig
  await assertFails(
    aliceDb.collection("appConfig").doc("global").update({
      trialLoginsEnabled: true,
      updatedAt: pastDate(1000),
    })
  );
});

// 21. ADMIN SEND USER TO A DIFFERENT ROOM (/roomTransfers/{userId})
test("Admin can send a user to another room and target user can acknowledge transfer; non-admins denied", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Regular user cannot transfer Bob
  await assertFails(
    aliceDb.collection("roomTransfers").doc(BOB_UID).set({
      userId: BOB_UID,
      targetRoomId: "room_dest",
      targetRoomTitle: "Main Stage",
      fromRoomId: "room_src",
      movedByAdminId: ALICE_UID,
      movedByAdminName: "Alice",
      reason: "Unauthorized move",
      consumed: false,
      createdAt: pastDate(2000),
    })
  );

  // Admin sends Bob to room_dest
  await assertSucceeds(
    adminDb.collection("roomTransfers").doc(BOB_UID).set({
      userId: BOB_UID,
      targetRoomId: "room_dest",
      targetRoomTitle: "Main Stage",
      fromRoomId: "room_src",
      movedByAdminId: ADMIN_UID,
      movedByAdminName: "Admin",
      reason: "Moved to main stage",
      consumed: false,
      createdAt: pastDate(1500),
    })
  );

  // Bob can read and mark his own transfer consumed, while Alice cannot read Bob's transfer
  await assertFails(aliceDb.collection("roomTransfers").doc(BOB_UID).get());
  await assertSucceeds(bobDb.collection("roomTransfers").doc(BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("roomTransfers").doc(BOB_UID).update({
      consumed: true,
    })
  );
});

// 22. GOOGLE PLAY BILLING METADATA ON BUY_POINTS TRANSACTIONS
test("BUY_POINTS ledger records verified Google Play Order ID and Product SKU", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("pointTransactions").doc("tx_play_1").set({
      type: "BUY_POINTS",
      buyerId: ALICE_UID,
      buyerName: "Alice",
      creatorId: "",
      creatorName: "",
      postId: "",
      pointsDelta: 10,
      grossCents: 1000,
      creatorShareCents: 0,
      platformShareCents: 0,
      playOrderId: "GPA.3312-4456-7789-01234",
      playProductId: "voxstage_points_10",
      createdAt: pastDate(1000),
    })
  );
});

// 23. PICTURE UPLOAD RATE LIMIT: MAX 10 PICTURES IN 10 MINUTES
test("Users are strictly limited to a maximum of 10 picture uploads in 10 minutes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = Date.now();
  const sampleBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
  const tenRecentTimestamps = Array.from({ length: 10 }, (_, idx) => now - (10 - idx) * 10_000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));

  // Record 10 recent picture uploads within the last 10 minutes
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pictureUploadTimestamps: tenRecentTimestamps,
      updatedAt: pastDate(2000),
    })
  );

  // 11th picture upload in a profile post MUST be denied by rules
  await assertFails(
    aliceDb.collection("profilePosts").doc("post_pic_11").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorTier: "USER",
      text: "11th picture",
      imageBase64: sampleBase64,
      locked: false,
      likedByUserIds: [],
      createdAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );

  // Text-only post (no picture) STILL succeeds even at picture limit
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("post_text_only").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorTier: "USER",
      text: "Text update works!",
      imageBase64: "",
      locked: false,
      likedByUserIds: [],
      createdAt: pastDate(1400),
      updatedAt: pastDate(1400),
    })
  );

  // User CANNOT tamper with active timestamps to bypass the 10-minute cooldown
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pictureUploadTimestamps: [],
      updatedAt: pastDate(1000),
    })
  );
});

// 24. FRIEND ATTENTION ALERTS IN PRIVATE MESSAGES (ACCEPTED FRIENDS ONLY, MAX 1 EVERY 5 MINUTES)
test("Friend attention alerts require accepted friendship and enforce a 5-minute cooldown", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(5000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Hey",
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Without an ACCEPTED friendship, sending an attention alert MUST fail
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).update({
      lastAlertSenderId: ALICE_UID,
      lastAlertSenderName: "Alice",
      lastAlertAtMillis: Date.now(),
      updatedAt: pastDate(2000),
    })
  );

  // Seed ACCEPTED friendship between Alice and Bob
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("friendships").doc(threadId).set({
      userIds: [ALICE_UID, BOB_UID],
      requesterId: ALICE_UID,
      requesterName: "Alice",
      requesterColorHex: "#0088FF",
      requesterTier: "USER",
      recipientId: BOB_UID,
      recipientName: "Bob",
      recipientColorHex: "#0088FF",
      recipientTier: "USER",
      status: "ACCEPTED",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  // First attention alert from Alice succeeds!
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      lastAlertSenderId: ALICE_UID,
      lastAlertSenderName: "Alice",
      lastAlertAtMillis: Date.now(),
      updatedAt: pastDate(1500),
    })
  );

  // Second attention alert within 5 minutes MUST be denied
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).update({
      lastAlertSenderId: ALICE_UID,
      lastAlertSenderName: "Alice",
      lastAlertAtMillis: Date.now(),
      updatedAt: pastDate(1000),
    })
  );

  // Simulate 5+ minutes elapsed -> Alice CAN send another attention alert
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("directThreads").doc(threadId).update({
      lastAlertAtMillis: Date.now() - 310_000,
    });
  });

  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      lastAlertSenderId: ALICE_UID,
      lastAlertSenderName: "Alice",
      lastAlertAtMillis: Date.now(),
      updatedAt: pastDate(500),
    })
  );
});

// 25. MUTUAL LIVE PUSH-TO-TALK OPT-IN ON DIRECT THREADS (self-only toggle)
test("Participants can toggle only their own UID in directThreads.pttEnabledUserIds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Walkie-talkie test",
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [],
      pttEnabledUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice enables live PTT for herself -> succeeds
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [ALICE_UID],
      updatedAt: pastDate(2000),
    })
  );

  // Alice CANNOT forcibly enable PTT for Bob -> denied
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(1800),
    })
  );

  // Bob enables live PTT for himself -> succeeds (now mutual!)
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(1500),
    })
  );

  // Bob CANNOT turn off Alice's PTT -> denied
  await assertFails(
    bobDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [BOB_UID],
      updatedAt: pastDate(1200),
    })
  );

  // Alice turns off her own PTT -> succeeds
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [BOB_UID],
      updatedAt: pastDate(1000),
    })
  );
});

// 26. DISAPPEARING PRIVATE MESSAGES & READ RECEIPTS OPTIONS
test("Participants can configure disappearing private messages and read receipts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      disappearingDurationSec: 0,
      readReceiptsDisabledByUserIds: [],
      readTimestampsByUserId: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice enables 5-minute disappearing messages -> succeeds
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      disappearingDurationSec: 300,
      updatedAt: pastDate(2500),
    })
  );

  // Invalid disappearing duration (e.g. 999s) -> denied
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).update({
      disappearingDurationSec: 999,
      updatedAt: pastDate(2400),
    })
  );

  // Alice sends a disappearing message with expiresAtMillis -> succeeds
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_ephem_1").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      senderTier: "USER",
      text: "Self-destructing note",
      expiresAtMillis: Date.now() + 300_000,
      createdAt: pastDate(2000),
    })
  );

  // Bob records read timestamp receipt -> succeeds
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).update({
      unreadByUserIds: [],
      readTimestampsByUserId: { [BOB_UID]: Date.now() },
      updatedAt: pastDate(1500),
    })
  );

  // Bob disables read receipts for himself -> succeeds; Bob CANNOT toggle Alice's read receipt setting -> denied
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).update({
      readReceiptsDisabledByUserIds: [BOB_UID],
      updatedAt: pastDate(1200),
    })
  );
  await assertFails(
    bobDb.collection("directThreads").doc(threadId).update({
      readReceiptsDisabledByUserIds: [BOB_UID, ALICE_UID],
      updatedAt: pastDate(1000),
    })
  );
});

// 27. SUPPORT TICKETS (Users create/view own tickets; Admin/Support respond and resolve; others denied)
test("Users can create and read their own support tickets; Support/Admin can reply and resolve", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("supportTickets").doc("ticket_1").set({
      requesterId: ALICE_UID,
      requesterName: "Alice",
      requesterTier: "USER",
      assignedStaffId: "",
      assignedStaffName: "",
      category: "ACCOUNT",
      subject: "Need help with profile",
      message: "Can support check my account settings?",
      status: "OPEN",
      staffResponse: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice can read her own ticket; Bob CANNOT read Alice's ticket
  await assertSucceeds(aliceDb.collection("supportTickets").doc("ticket_1").get());
  await assertFails(bobDb.collection("supportTickets").doc("ticket_1").get());

  // Non-staff CANNOT inject a staffResponse
  await assertFails(
    aliceDb.collection("supportTickets").doc("ticket_1").update({
      staffResponse: "Fake response",
      status: "RESOLVED",
      updatedAt: pastDate(2000),
    })
  );

  // Admin can read, reply, and resolve the ticket
  await assertSucceeds(adminDb.collection("supportTickets").doc("ticket_1").get());
  await assertSucceeds(
    adminDb.collection("supportTickets").doc("ticket_1").update({
      status: "RESOLVED",
      staffResponse: "All fixed for you!",
      assignedStaffId: ADMIN_UID,
      assignedStaffName: "VoxStage Admin",
      updatedAt: pastDate(1500),
    })
  );
});

// 28. SUGGESTED FRIENDS (acceptedFriendIds synced on userDirectory)
test("Users can sync acceptedFriendIds on their own userDirectory document for friend-of-friend discovery", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      bio: "Hello",
      avatarColorHex: "#0088FF",
      photoBase64: "",
      staffTier: "USER",
      postCount: 0,
      dmPolicy: "OPEN",
      online: true,
      lastActiveAt: ts,
      acceptedFriendIds: [BOB_UID],
      updatedAt: ts,
    })
  );
});

// 29. USER FOLLOWS (Followers & Following counts)
test("Users can follow and unfollow others via userFollows; cannot spoof or self-follow", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);
  const followId = `${ALICE_UID}__${BOB_UID}`;

  // Alice follows Bob
  await assertSucceeds(
    aliceDb.collection("userFollows").doc(followId).set({
      followerId: ALICE_UID,
      followingId: BOB_UID,
      createdAt: ts,
    })
  );

  // Anyone can read followers/following counts
  await assertSucceeds(
    bobDb.collection("userFollows").where("followingId", "==", BOB_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("userFollows").where("followerId", "==", ALICE_UID).get()
  );

  // Alice CANNOT follow herself
  await assertFails(
    aliceDb.collection("userFollows").doc(`${ALICE_UID}__${ALICE_UID}`).set({
      followerId: ALICE_UID,
      followingId: ALICE_UID,
      createdAt: ts,
    })
  );

  // Bob CANNOT spoof a follow on Alice's behalf or delete Alice's follow
  await assertFails(
    bobDb.collection("userFollows").doc(`${ALICE_UID}__charlie_999`).set({
      followerId: ALICE_UID,
      followingId: "charlie_999",
      createdAt: ts,
    })
  );
  await assertFails(bobDb.collection("userFollows").doc(followId).delete());

  // Alice can unfollow Bob
  await assertSucceeds(aliceDb.collection("userFollows").doc(followId).delete());
});

// 30. ADMIN AUTO-ENABLES PTT FOR USER IN PRIVATE CHAT
test("Admin enabling PTT in a private chat automatically enables it for the other participant too", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(5000);
  const threadId = `${ADMIN_UID}_${ALICE_UID}`;

  await assertSucceeds(
    adminDb.collection("directThreads").doc(threadId).set({
      participantIds: [ADMIN_UID, ALICE_UID],
      userAId: ADMIN_UID,
      userAName: "VoxStage Admin",
      userAColorHex: "#D31B2E",
      userATier: "ADMIN",
      userBId: ALICE_UID,
      userBName: "Alice",
      userBColorHex: "#1877F2",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Regular user Alice CANNOT auto-enable PTT for both participants
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [ADMIN_UID, ALICE_UID],
      updatedAt: pastDate(3000),
    })
  );

  // Admin CAN enable PTT for both Admin and Alice automatically
  await assertSucceeds(
    adminDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [ADMIN_UID, ALICE_UID],
      updatedAt: pastDate(2000),
    })
  );

  // Admin CAN turn off PTT for the chat
  await assertSucceeds(
    adminDb.collection("directThreads").doc(threadId).update({
      pttEnabledUserIds: [],
      updatedAt: pastDate(1000),
    })
  );
});

// 31. FREE MEMBER 2-DISCOVERY-PHOTOS-PER-WEEK LIMIT + PROFILE AGE/SEX VALIDATION
test("Free members can post at most 2 Discovery photos per week; paid members unlimited; age/sex validated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(5000);
  const nowMs = Date.now();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));

  // Valid age & sex updates succeed on users and userDirectory
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      sex: "FEMALE",
      age: 26,
      updatedAt: pastDate(4500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      bio: "Hello",
      avatarColorHex: "#1877F2",
      photoBase64: "",
      staffTier: "USER",
      postCount: 0,
      dmPolicy: "OPEN",
      sex: "FEMALE",
      age: 26,
      online: true,
      acceptedFriendIds: [],
      lastActiveAt: pastDate(4500),
      updatedAt: pastDate(4500),
    })
  );

  // Invalid sex or age < 18 denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      sex: "INVALID_SEX",
      updatedAt: pastDate(4400),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      age: 15,
      updatedAt: pastDate(4300),
    })
  );

  // Free member posts 2 Discovery photos (recording discoveryPhotoTimestamps)
  const batch1 = aliceDb.batch();
  batch1.set(aliceDb.collection("profilePosts").doc("disc_pic_1"), {
    authorId: ALICE_UID,
    authorName: "Alice",
    authorHandle: "alice_vox",
    authorColorHex: "#1877F2",
    authorPhotoBase64: "",
    authorTier: "USER",
    text: "First discovery pic",
    imageBase64: "ZmFrZV9pbWFnZV8x",
    likedByUserIds: [],
    createdAt: pastDate(4000),
    updatedAt: pastDate(4000),
  });
  batch1.update(aliceDb.collection("users").doc(ALICE_UID), {
    pictureUploadTimestamps: [nowMs - 4000],
    discoveryPhotoTimestamps: [nowMs - 4000],
    updatedAt: pastDate(4000),
  });
  await assertSucceeds(batch1.commit());

  const batch2 = aliceDb.batch();
  batch2.set(aliceDb.collection("profilePosts").doc("disc_pic_2"), {
    authorId: ALICE_UID,
    authorName: "Alice",
    authorHandle: "alice_vox",
    authorColorHex: "#1877F2",
    authorPhotoBase64: "",
    authorTier: "USER",
    text: "Second discovery pic",
    imageBase64: "ZmFrZV9pbWFnZV8y",
    likedByUserIds: [],
    createdAt: pastDate(3000),
    updatedAt: pastDate(3000),
  });
  batch2.update(aliceDb.collection("users").doc(ALICE_UID), {
    pictureUploadTimestamps: [nowMs - 4000, nowMs - 3000],
    discoveryPhotoTimestamps: [nowMs - 4000, nowMs - 3000],
    updatedAt: pastDate(3000),
  });
  await assertSucceeds(batch2.commit());

  // 3rd Discovery photo within 7 days is DENIED for free member!
  await assertFails(
    aliceDb.collection("profilePosts").doc("disc_pic_3").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#1877F2",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Third discovery pic should fail",
      imageBase64: "ZmFrZV9pbWFnZV8z",
      likedByUserIds: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Free member CANNOT wipe discoveryPhotoTimestamps inside the 7-day window
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      discoveryPhotoTimestamps: [],
      updatedAt: pastDate(1900),
    })
  );

  // Text-only post STILL succeeds for free member even after 2 weekly photos
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("disc_text_only").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#1877F2",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Text-only status update still works!",
      imageBase64: "",
      likedByUserIds: [],
      createdAt: pastDate(1800),
      updatedAt: pastDate(1800),
    })
  );

  // Once Alice becomes a Paid Member (buys points), 3rd Discovery photo SUCCEEDS!
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 5,
      totalSpentCents: 500,
      updatedAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("profilePosts").doc("disc_pic_3_paid").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#1877F2",
      authorPhotoBase64: "",
      authorTier: "USER",
      text: "Paid member unlimited photo!",
      imageBase64: "ZmFrZV9pbWFnZV8z",
      likedByUserIds: [],
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
});

// 32. PRIVATE MESSAGE REACTIONS & SCREENSHOT NOTIFICATION
test("DM participants can toggle their own emoji reaction and send screenshot notices; spoofing others or invalid emojis denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  const ts = pastDate(5000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#1877F2",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "",
      lastMessageSenderId: "",
      unreadByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice sends a normal message and Bob sends a screenshot notice
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_react_1").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      senderTier: "USER",
      text: "Hey Bob! 👍",
      messageType: "TEXT",
      reactionsByUserId: {},
      createdAt: pastDate(4000),
    })
  );
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_ss_1").set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Bob took a screenshot of this chat",
      messageType: "SCREENSHOT",
      createdAt: pastDate(3500),
    })
  );

  // Bob reacts with ❤️ to Alice's message
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_react_1").update({
      reactionsByUserId: { [BOB_UID]: "❤️" },
    })
  );

  // Alice adds her own 👍 reaction while keeping Bob's ❤️
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_react_1").update({
      reactionsByUserId: { [BOB_UID]: "❤️", [ALICE_UID]: "👍" },
    })
  );

  // Bob CANNOT remove or change Alice's reaction, or use an invalid emoji, or edit message text
  await assertFails(
    bobDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_react_1").update({
      reactionsByUserId: { [BOB_UID]: "❤️" },
    })
  );
  await assertFails(
    bobDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_react_1").update({
      reactionsByUserId: { [BOB_UID]: "💀", [ALICE_UID]: "👍" },
    })
  );
  await assertFails(
    bobDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_react_1").update({
      text: "Tampered",
    })
  );

  // Non-participant Charlie CANNOT react
  await assertFails(
    charlieDb.collection("directThreads").doc(threadId).collection("messages").doc("dm_react_1").update({
      reactionsByUserId: { [BOB_UID]: "❤️", [ALICE_UID]: "👍", charlie_999: "🔥" },
    })
  );
});

// 33. LONG-PRESS PRIVATE MESSAGE REPORTING VIA SAFETY REPORTS
test("DM participants can report a private message to safetyReports; non-participants denied; Support can list reports", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  const ts = pastDate(5000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: false,
      addedByUid: ADMIN_UID,
      addedByName: "Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#1877F2",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Offensive DM",
      lastMessageSenderId: BOB_UID,
      unreadByUserIds: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Participant Alice CAN report a private message in her thread
  await assertSucceeds(
    aliceDb.collection("safetyReports").doc("rep_dm_1").set({
      reporterId: ALICE_UID,
      reporterName: "Alice",
      roomId: threadId,
      roomTitle: "Private Message",
      roomHostId: ALICE_UID,
      targetUserId: BOB_UID,
      targetName: "Bob",
      category: "HARASSMENT",
      details: "Reported DM: \"Offensive DM\"",
      status: "OPEN",
      resolutionNote: "",
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Non-participant Charlie CANNOT file a DM report for Alice & Bob's thread
  await assertFails(
    charlieDb.collection("safetyReports").doc("rep_dm_spoof").set({
      reporterId: "charlie_999",
      reporterName: "Charlie",
      roomId: threadId,
      roomTitle: "Private Message",
      roomHostId: "charlie_999",
      targetUserId: BOB_UID,
      targetName: "Bob",
      category: "HARASSMENT",
      details: "Fake report",
      status: "OPEN",
      resolutionNote: "",
      createdAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );

  // Support staff CAN list and review safetyReports
  await assertSucceeds(supportDb.collection("safetyReports").get());
});

// 34. ADMIN SENDS USER TO A SUPPORT MEMBER'S PRIVATE CHAT
test("Admin can create/update a user-to-support private chat, post a SUPPORT_HANDOFF intro, and send a SUPPORT_DM transfer", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();
  const ts = pastDate(5000);
  const supportThreadId = `${ALICE_UID}_${SUPPORT_UID}`;

  // Admin opens a 1-on-1 private thread between Alice and Support Member
  await assertSucceeds(
    adminDb.collection("directThreads").doc(supportThreadId).set({
      participantIds: [ALICE_UID, SUPPORT_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#1877F2",
      userATier: "USER",
      userBId: SUPPORT_UID,
      userBName: "Sam Support",
      userBColorHex: "#1565C0",
      userBTier: "SUPPORT",
      lastMessageText: "Admin connected you with Sam Support for help.",
      lastMessageSenderId: ADMIN_UID,
      unreadByUserIds: [ALICE_UID, SUPPORT_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Admin posts a SUPPORT_HANDOFF system message into that thread
  await assertSucceeds(
    adminDb.collection("directThreads").doc(supportThreadId).collection("messages").doc("msg_handoff_1").set({
      threadId: supportThreadId,
      participantIds: [ALICE_UID, SUPPORT_UID],
      senderId: ADMIN_UID,
      senderName: "VoxStage Admin",
      senderTier: "ADMIN",
      text: "Admin connected Alice with Sam Support: Billing help",
      messageType: "SUPPORT_HANDOFF",
      createdAt: pastDate(4000),
    })
  );

  // Admin sends SUPPORT_DM transfer directive to Alice
  await assertSucceeds(
    adminDb.collection("roomTransfers").doc(ALICE_UID).set({
      userId: ALICE_UID,
      targetRoomId: supportThreadId,
      targetRoomTitle: "Support Chat: Sam Support",
      fromRoomId: "",
      movedByAdminId: ADMIN_UID,
      movedByAdminName: "VoxStage Admin",
      reason: "Billing help",
      transferType: "SUPPORT_DM",
      targetThreadId: supportThreadId,
      targetSupportUserId: SUPPORT_UID,
      targetSupportName: "Sam Support",
      consumed: false,
      createdAt: pastDate(3500),
    })
  );

  // Alice and Support can both read the handoff message and Alice consumes the transfer
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(supportThreadId).collection("messages").doc("msg_handoff_1").get()
  );
  await assertSucceeds(
    supportDb.collection("directThreads").doc(supportThreadId).collection("messages").doc("msg_handoff_1").get()
  );
  await assertSucceeds(
    aliceDb.collection("roomTransfers").doc(ALICE_UID).update({ consumed: true })
  );

  // Regular user Alice CANNOT issue a SUPPORT_DM transfer to Bob
  await assertFails(
    aliceDb.collection("roomTransfers").doc(BOB_UID).set({
      userId: BOB_UID,
      targetRoomId: `${BOB_UID}_${SUPPORT_UID}`,
      targetRoomTitle: "Support Chat",
      fromRoomId: "",
      movedByAdminId: ALICE_UID,
      movedByAdminName: "Alice",
      reason: "Fake",
      transferType: "SUPPORT_DM",
      targetThreadId: `${BOB_UID}_${SUPPORT_UID}`,
      targetSupportUserId: SUPPORT_UID,
      targetSupportName: "Sam Support",
      consumed: false,
      createdAt: pastDate(2000),
    })
  );
});

// 35. APPEARANCE STATUS (ONLINE/IDLE/OFFLINE) & MONOTONIC TIME SPENT ON APP
test("Users can set appearanceStatus (ONLINE/IDLE/OFFLINE) and monotonically increment totalTimeSpentSec; invalid values denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));

  // Valid appearanceStatus + monotonic totalTimeSpentSec increment
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      appearanceStatus: "IDLE",
      totalTimeSpentSec: 120,
      updatedAt: pastDate(4000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      appearanceStatus: "OFFLINE",
      totalTimeSpentSec: 240,
      updatedAt: pastDate(3000),
    })
  );

  // Directory sync with true online=true + appearanceStatus='OFFLINE' + totalTimeSpentSec=240 succeeds
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      bio: "Hello",
      avatarColorHex: "#1877F2",
      photoBase64: "",
      staffTier: "USER",
      postCount: 0,
      dmPolicy: "OPEN",
      online: true,
      appearanceStatus: "OFFLINE",
      totalTimeSpentSec: 240,
      acceptedFriendIds: [],
      lastActiveAt: pastDate(3000),
      updatedAt: pastDate(3000),
    })
  );

  // Invalid appearanceStatus denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      appearanceStatus: "INVISIBLE_FAKE",
      updatedAt: pastDate(2500),
    })
  );

  // Decreasing totalTimeSpentSec or jumping > 86400s in one update denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalTimeSpentSec: 100,
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalTimeSpentSec: 500000,
      updatedAt: pastDate(1500),
    })
  );
});

// 36. ADMIN APP-WIDE BROADCAST MESSAGES TO ALL USERS
test("Admin can broadcast app-wide messages to all users and delete them; non-admins/support denied create/delete; unauthenticated denied read", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const broadcastPayload = {
    title: "Maintenance Window",
    message: "VoxStage will refresh audio servers tonight at 11 PM UTC.",
    severity: "INFO",
    senderId: ADMIN_UID,
    senderName: "VoxStage Admin",
    createdAt: pastDate(4000),
  };

  // Admin can create an app-wide broadcast
  await assertSucceeds(
    adminDb.collection("appBroadcasts").doc("bcast_1").set(broadcastPayload)
  );

  // Regular signed-in user Alice can read and list broadcasts
  await assertSucceeds(aliceDb.collection("appBroadcasts").doc("bcast_1").get());
  await assertSucceeds(aliceDb.collection("appBroadcasts").get());

  // Unauthenticated user CANNOT read or list broadcasts
  await assertFails(unauthDb.collection("appBroadcasts").doc("bcast_1").get());
  await assertFails(unauthDb.collection("appBroadcasts").get());

  // Regular user and Support Staff CANNOT create broadcasts
  await assertFails(
    aliceDb.collection("appBroadcasts").doc("bcast_alice").set({
      ...broadcastPayload,
      senderId: ALICE_UID,
      senderName: "Alice",
    })
  );
  await assertFails(
    supportDb.collection("appBroadcasts").doc("bcast_support").set({
      ...broadcastPayload,
      senderId: SUPPORT_UID,
      senderName: "Sam Support",
    })
  );

  // Updates are immutable (even for Admin)
  await assertFails(
    adminDb.collection("appBroadcasts").doc("bcast_1").update({
      title: "Edited Title",
    })
  );

  // Non-admin CANNOT delete broadcasts; Admin CAN delete
  await assertFails(aliceDb.collection("appBroadcasts").doc("bcast_1").delete());
  await assertFails(supportDb.collection("appBroadcasts").doc("bcast_1").delete());
  await assertSucceeds(adminDb.collection("appBroadcasts").doc("bcast_1").delete());
});

// 37. PINNING & UNPINNING MESSAGES IN PRIVATE CHATS
test("DM participants can pin and unpin messages; spoofed pinner, non-participants, and blocked threads denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieUid = "charlie_999";
  const charlieDb = testEnv.authenticatedContext(charlieUid).firestore();
  const threadId = `${ALICE_UID}_${BOB_UID}`;
  const ts = pastDate(5000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));

  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#1877F2",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#E91E63",
      userBTier: "USER",
      lastMessageText: "Meet at 8pm!",
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice pins a message
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(threadId).update({
      pinnedMessageId: "msg_1",
      pinnedMessageText: "Meet at 8pm!",
      pinnedMessageSenderName: "Alice",
      pinnedByUserId: ALICE_UID,
      pinnedByName: "Alice",
      pinnedAtMillis: Date.now() - 2000,
      updatedAt: pastDate(3000),
    })
  );

  // Bob (other participant) can unpin the message
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).update({
      pinnedMessageId: "",
      pinnedMessageText: "",
      pinnedMessageSenderName: "",
      pinnedByUserId: "",
      pinnedByName: "",
      pinnedAtMillis: 0,
      updatedAt: pastDate(2500),
    })
  );

  // Spoofed pinnedByUserId is denied
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).update({
      pinnedMessageId: "msg_2",
      pinnedMessageText: "Spoof",
      pinnedMessageSenderName: "Bob",
      pinnedByUserId: BOB_UID,
      pinnedByName: "Bob",
      pinnedAtMillis: Date.now() - 1000,
      updatedAt: pastDate(2000),
    })
  );

  // Non-participant Charlie cannot pin
  await assertFails(
    charlieDb.collection("directThreads").doc(threadId).update({
      pinnedMessageId: "msg_3",
      pinnedMessageText: "Intrusion",
      pinnedMessageSenderName: "Charlie",
      pinnedByUserId: charlieUid,
      pinnedByName: "Charlie",
      pinnedAtMillis: Date.now() - 1000,
      updatedAt: pastDate(1800),
    })
  );

  // Blocked thread cannot pin messages
  await assertSucceeds(
    bobDb.collection("directThreads").doc(threadId).update({
      blockedByUserIds: [BOB_UID],
      unreadByUserIds: [],
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    aliceDb.collection("directThreads").doc(threadId).update({
      pinnedMessageId: "msg_4",
      pinnedMessageText: "Blocked pin",
      pinnedMessageSenderName: "Alice",
      pinnedByUserId: ALICE_UID,
      pinnedByName: "Alice",
      pinnedAtMillis: Date.now() - 1000,
      updatedAt: pastDate(1000),
    })
  );
});

test("38. Pro Membership ($9.99/mo), Creator Spendable Points, and Reels (Pro-only <=30s, $5 Boost, Likes/Comments/Shares)", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, { displayName: "Alice", handle: "alice" }));
    await db.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, { displayName: "Bob", handle: "bob" }));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Free user Alice CANNOT spoof proMember in userDirectory before activating Pro
  await assertFails(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      avatarColorHex: "#1E6BFF",
      staffTier: "USER",
      proMember: true,
      proExpiresAtMillis: Date.now() + 30 * 24 * 60 * 60 * 1000,
      updatedAt: pastDate(4000),
    })
  );

  // Free user Alice CANNOT upload a Reel
  await assertFails(
    aliceDb.collection("reels").doc("reel_free_denied").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice",
      authorColorHex: "#1E6BFF",
      authorTier: "USER",
      caption: "Trying as free user",
      thumbnailBase64: "AAAA",
      durationSec: 15,
      chunkCount: 1,
      mimeType: "video/mp4",
      likedByUserIds: [],
      commentCount: 0,
      shareCount: 0,
      createdAt: pastDate(3900),
      updatedAt: pastDate(3900),
    })
  );

  // Alice activates Pro Membership ($9.99/mo = 999 cents) + updates userDirectory + logs PRO_MEMBERSHIP transaction
  const proExpiry = Date.now() + 30 * 24 * 60 * 60 * 1000;
  const proBatch = aliceDb.batch();
  proBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    proMember: true,
    proExpiresAtMillis: proExpiry,
    totalSpentCents: 999,
    updatedAt: pastDate(3800),
  });
  proBatch.set(aliceDb.collection("userDirectory").doc(ALICE_UID), {
    userId: ALICE_UID,
    displayName: "Alice",
    handle: "alice",
    avatarColorHex: "#1E6BFF",
    staffTier: "USER",
    proMember: true,
    proExpiresAtMillis: proExpiry,
    updatedAt: pastDate(3800),
  });
  proBatch.set(aliceDb.collection("pointTransactions").doc("tx_pro_alice"), {
    type: "PRO_MEMBERSHIP",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "",
    pointsDelta: 0,
    grossCents: 999,
    creatorShareCents: 0,
    platformShareCents: 999,
    createdAt: pastDate(3800),
  });
  await assertSucceeds(proBatch.commit());

  // Pro user Alice CANNOT upload a Reel longer than 30 seconds
  await assertFails(
    aliceDb.collection("reels").doc("reel_too_long").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice",
      authorColorHex: "#1E6BFF",
      authorTier: "USER",
      caption: "Too long",
      thumbnailBase64: "AAAA",
      durationSec: 31,
      chunkCount: 1,
      mimeType: "video/mp4",
      likedByUserIds: [],
      commentCount: 0,
      shareCount: 0,
      createdAt: pastDate(3700),
      updatedAt: pastDate(3700),
    })
  );

  // Pro user Alice CAN upload a valid 24-second Reel + video chunk
  await assertSucceeds(
    aliceDb.collection("reels").doc("reel_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice",
      authorColorHex: "#1E6BFF",
      authorTier: "USER",
      caption: "Sunset vibes",
      thumbnailBase64: "AAAA",
      durationSec: 24,
      chunkCount: 1,
      mimeType: "video/mp4",
      likedByUserIds: [],
      commentCount: 0,
      shareCount: 0,
      createdAt: pastDate(3600),
      updatedAt: pastDate(3600),
    })
  );
  await assertSucceeds(
    aliceDb.collection("reels").doc("reel_1").collection("chunks").doc("chunk_0").set({
      reelId: "reel_1",
      authorId: ALICE_UID,
      chunkIndex: 0,
      dataBase64: "QUFBQQ==",
      createdAt: pastDate(3550),
    })
  );

  // Alice CANNOT boost without paying 5 points
  await assertFails(
    aliceDb.collection("reels").doc("reel_1").update({
      boostedAtMillis: Date.now() - 1000,
      boostedUntilMillis: Date.now() + 86400000,
      updatedAt: pastDate(3400),
    })
  );

  // Alice buys 5 points ($5) then boosts her Reel to top via atomic batch!
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 5,
      totalSpentCents: 1499,
      updatedAt: pastDate(3300),
    })
  );
  const boostBatch = aliceDb.batch();
  boostBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 0,
    updatedAt: pastDate(3200),
  });
  boostBatch.update(aliceDb.collection("reels").doc("reel_1"), {
    boostedAtMillis: Date.now() - 1000,
    boostedUntilMillis: Date.now() + 86400000,
    updatedAt: pastDate(3200),
  });
  boostBatch.set(aliceDb.collection("pointTransactions").doc("tx_boost_reel_1"), {
    type: "BOOST_REEL",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "reel_1",
    pointsDelta: -5,
    grossCents: 500,
    creatorShareCents: 0,
    platformShareCents: 500,
    createdAt: pastDate(3200),
  });
  await assertSucceeds(boostBatch.commit());

  // Bob CAN like, comment on, and share Alice's Reel
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").update({
      likedByUserIds: [BOB_UID],
      updatedAt: pastDate(3000),
    })
  );
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").collection("comments").doc("c1").set({
      reelId: "reel_1",
      authorId: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob",
      authorColorHex: "#10B981",
      authorTier: "USER",
      text: "Awesome reel!",
      createdAt: pastDate(2900),
      updatedAt: pastDate(2900),
    })
  );
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").update({
      commentCount: 1,
      updatedAt: pastDate(2850),
    })
  );
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").update({
      shareCount: 1,
      updatedAt: pastDate(2800),
    })
  );
});

// 39. PROFILE BOOST (5 PTS) + CUSTOM PTT THEMES & WALKIE CHIRPS SHOP (ANTI-SPOOFING)
test("Users can spend points to promote their profile to top of Discovery and buy/equip custom PTT themes & walkie chirps; spoofing is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      avatarColorHex: "#0088FF",
      staffTier: "USER",
      proMember: false,
      proExpiresAtMillis: 0,
      profileBoostedUntilMillis: 0,
      updatedAt: ts,
    })
  );

  // Alice CANNOT spoof profileBoostedUntilMillis in userDirectory without paying 5 pts
  await assertFails(
    aliceDb.collection("userDirectory").doc(ALICE_UID).update({
      profileBoostedUntilMillis: Date.now() + 86400000,
      updatedAt: pastDate(4800),
    })
  );

  // Alice CANNOT equip an unowned PTT theme or chirp for free
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedPttTheme: "NEON_MAGENTA",
      updatedAt: pastDate(4700),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedPttChirp: "ROGER_BEEP",
      updatedAt: pastDate(4650),
    })
  );

  // Alice buys 10 points ($10)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 10,
      totalSpentCents: 1000,
      updatedAt: pastDate(4500),
    })
  );

  // Alice spends 5 points to promote her profile for 24h (atomic batch updating users + userDirectory + BOOST_PROFILE tx)
  const boostUntil = Date.now() + 86400000;
  const profileBoostBatch = aliceDb.batch();
  profileBoostBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 5,
    profileBoostedUntilMillis: boostUntil,
    updatedAt: pastDate(4200),
  });
  profileBoostBatch.update(aliceDb.collection("userDirectory").doc(ALICE_UID), {
    profileBoostedUntilMillis: boostUntil,
    updatedAt: pastDate(4200),
  });
  profileBoostBatch.set(aliceDb.collection("pointTransactions").doc("tx_boost_profile_1"), {
    type: "BOOST_PROFILE",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "",
    pointsDelta: -5,
    grossCents: 500,
    creatorShareCents: 0,
    platformShareCents: 500,
    createdAt: pastDate(4200),
  });
  await assertSucceeds(profileBoostBatch.commit());

  // Alice buys Neon Magenta PTT Theme (2 pts) and auto-equips it
  const buyThemeBatch = aliceDb.batch();
  buyThemeBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 3,
    ownedPttThemes: ["NEON_MAGENTA"],
    selectedPttTheme: "NEON_MAGENTA",
    updatedAt: pastDate(4000),
  });
  buyThemeBatch.set(aliceDb.collection("pointTransactions").doc("tx_ptt_theme_1"), {
    type: "BUY_PTT_ITEM",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "THEME_NEON_MAGENTA",
    pointsDelta: -2,
    grossCents: 200,
    creatorShareCents: 0,
    platformShareCents: 200,
    createdAt: pastDate(4000),
  });
  await assertSucceeds(buyThemeBatch.commit());

  // Alice buys Roger Beep Walkie Chirp (2 pts) and auto-equips it
  const buyChirpBatch = aliceDb.batch();
  buyChirpBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 1,
    ownedPttChirps: ["ROGER_BEEP"],
    selectedPttChirp: "ROGER_BEEP",
    updatedAt: pastDate(3800),
  });
  buyChirpBatch.set(aliceDb.collection("pointTransactions").doc("tx_ptt_chirp_1"), {
    type: "BUY_PTT_ITEM",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "CHIRP_ROGER_BEEP",
    pointsDelta: -2,
    grossCents: 200,
    creatorShareCents: 0,
    platformShareCents: 200,
    createdAt: pastDate(3800),
  });
  await assertSucceeds(buyChirpBatch.commit());

  // Alice CAN freely switch between CLASSIC and owned NEON_MAGENTA without spending points
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedPttTheme: "CLASSIC",
      selectedPttChirp: "STANDARD",
      updatedAt: pastDate(3600),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedPttTheme: "NEON_MAGENTA",
      selectedPttChirp: "ROGER_BEEP",
      updatedAt: pastDate(3500),
    })
  );
});

// 40. SPOTLIGHT FRAME (5 PTS / 24H), FREE 7-DAY LOGIN STREAK (+0.5 PT), INVITE-A-FRIEND (+2 PTS EACH) & MUTUAL DM TALK STREAKS
test("24-Hour Spotlight Frame, Free-User 7-Day Login Streak (+0.5 pt), Invite-a-Friend (+2 pts each), and Mutual DM Talk Streaks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const unverifiedUid = "user_unverified_ref";
  const unverifiedDb = testEnv.authenticatedContext(unverifiedUid, { email: "unv@example.com", email_verified: false }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      referralCode: "VOX-ALICE1",
    })
  );
  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      bio: "",
      avatarColorHex: "#0088FF",
      photoBase64: "",
      staffTier: "USER",
      referralCode: "VOX-ALICE1",
      postCount: 0,
      dmPolicy: "OPEN",
      online: true,
      updatedAt: pastDate(6000),
    })
  );
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertSucceeds(
    bobDb.collection("userDirectory").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      handle: "bob_vox",
      bio: "",
      avatarColorHex: "#0088FF",
      photoBase64: "",
      staffTier: "USER",
      postCount: 0,
      dmPolicy: "OPEN",
      online: true,
      updatedAt: pastDate(6000),
    })
  );

  // 1) Spotlight Frame anti-spoof: Alice cannot claim spotlightFrameUntilMillis on directory without buying it
  await assertFails(
    aliceDb.collection("userDirectory").doc(ALICE_UID).update({
      spotlightFrameUntilMillis: Date.now() + 86400000,
      spotlightFrameColorHex: "#00F5FF",
      updatedAt: pastDate(5800),
    })
  );

  // Alice buys 10 pts, then spends 5 pts on 24-Hour Glowing Neon Spotlight Frame
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 10,
      totalSpentCents: 1000,
      updatedAt: pastDate(5600),
    })
  );
  const frameUntil = Date.now() + 86400000;
  const frameBatch = aliceDb.batch();
  frameBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 5,
    spotlightFrameUntilMillis: frameUntil,
    spotlightFrameColorHex: "#FF2E93",
    updatedAt: pastDate(5400),
  });
  frameBatch.update(aliceDb.collection("userDirectory").doc(ALICE_UID), {
    spotlightFrameUntilMillis: frameUntil,
    spotlightFrameColorHex: "#FF2E93",
    updatedAt: pastDate(5400),
  });
  frameBatch.set(aliceDb.collection("pointTransactions").doc("tx_spotlight_1"), {
    type: "BUY_SPOTLIGHT_FRAME",
    buyerId: ALICE_UID,
    buyerName: "Alice",
    creatorId: "",
    creatorName: "",
    postId: "",
    pointsDelta: -5,
    grossCents: 500,
    creatorShareCents: 0,
    platformShareCents: 500,
    createdAt: pastDate(5400),
  });
  await assertSucceeds(frameBatch.commit());

  // 2) Free-user Daily Login Streak (+0.5 pt after 7-day streak; two 7-day streaks merge to +1 full pt)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).update({
      loginStreakDays: 6,
      lastLoginDayKey: "2026-11-23",
    });
  });
  // Cannot claim reward on same day
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      loginStreakDays: 7,
      lastLoginDayKey: "2026-11-23",
      lastStreakRewardDayKey: "2026-11-23",
      halfPointsBalance: 1,
      updatedAt: pastDate(5000),
    })
  );
  // Day 7 free-user login awards +0.5 pt (halfPointsBalance = 1)
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      loginStreakDays: 7,
      lastLoginDayKey: "2026-11-24",
      lastStreakRewardDayKey: "2026-11-24",
      halfPointsBalance: 1,
      updatedAt: pastDate(4900),
    })
  );
  // Second 7-day streak (Day 13 -> Day 14) merges halfPointsBalance 1 -> 0 and credits +1 full point
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).update({
      loginStreakDays: 13,
      lastLoginDayKey: "2026-11-30",
    });
  });
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      loginStreakDays: 14,
      lastLoginDayKey: "2026-12-01",
      lastStreakRewardDayKey: "2026-12-01",
      halfPointsBalance: 0,
      pointsBalance: 1,
      updatedAt: pastDate(4800),
    })
  );

  // 3) Invite-a-Friend Point Bonus (+2 pts each after verified account + first PTT clip)
  // Self-referral denied
  await assertFails(
    aliceDb.collection("referrals").doc(ALICE_UID).set({
      inviteeId: ALICE_UID,
      inviteeName: "Alice",
      inviterId: ALICE_UID,
      inviterName: "Alice",
      referralCode: "VOX-ALICE1",
      accountVerified: true,
      firstPttSent: false,
      bonusClaimed: false,
      bonusPointsEach: 2,
      createdAt: pastDate(4700),
      updatedAt: pastDate(4700),
    })
  );
  // Unverified user cannot claim referral bonus even if linked
  await assertSucceeds(unverifiedDb.collection("users").doc(unverifiedUid).set(validProfilePayload(unverifiedUid)));
  await assertSucceeds(
    unverifiedDb.collection("referrals").doc(unverifiedUid).set({
      inviteeId: unverifiedUid,
      inviteeName: "Unverified",
      inviterId: ALICE_UID,
      inviterName: "Alice",
      referralCode: "VOX-ALICE1",
      accountVerified: false,
      firstPttSent: false,
      bonusClaimed: false,
      bonusPointsEach: 2,
      createdAt: pastDate(4650),
      updatedAt: pastDate(4650),
    })
  );
  await assertFails(
    unverifiedDb.collection("referrals").doc(unverifiedUid).update({
      accountVerified: true,
      firstPttSent: true,
      bonusClaimed: true,
      updatedAt: pastDate(4600),
    })
  );

  // Verified Bob links Alice's invite code, then completes verification + first PTT clip -> +2 pts each!
  await assertSucceeds(
    bobDb.collection("referrals").doc(BOB_UID).set({
      inviteeId: BOB_UID,
      inviteeName: "Bob",
      inviterId: ALICE_UID,
      inviterName: "Alice",
      referralCode: "VOX-ALICE1",
      accountVerified: true,
      firstPttSent: false,
      bonusClaimed: false,
      bonusPointsEach: 2,
      createdAt: pastDate(4500),
      updatedAt: pastDate(4500),
    })
  );
  const referralBatch = bobDb.batch();
  referralBatch.update(bobDb.collection("referrals").doc(BOB_UID), {
    accountVerified: true,
    firstPttSent: true,
    bonusClaimed: true,
    updatedAt: pastDate(4400),
  });
  referralBatch.update(bobDb.collection("users").doc(BOB_UID), {
    pointsBalance: 3,
    referralBonusClaimed: true,
    referredByUserId: ALICE_UID,
    updatedAt: pastDate(4400),
  });
  referralBatch.update(bobDb.collection("users").doc(ALICE_UID), {
    pointsBalance: 7,
    referralEarnedPoints: 2,
    updatedAt: pastDate(4400),
  });
  referralBatch.set(bobDb.collection("pointTransactions").doc("tx_ref_bonus_1"), {
    type: "REFERRAL_BONUS",
    buyerId: BOB_UID,
    buyerName: "Bob",
    creatorId: ALICE_UID,
    creatorName: "Alice",
    postId: "",
    pointsDelta: 2,
    grossCents: 0,
    creatorShareCents: 0,
    platformShareCents: 0,
    createdAt: pastDate(4400),
  });
  await assertSucceeds(referralBatch.commit());

  // Cannot re-claim already claimed referral bonus
  await assertFails(
    bobDb.collection("referrals").doc(BOB_UID).update({
      bonusClaimed: true,
      updatedAt: pastDate(4300),
    })
  );

  // 4) Mutual Talk Streaks in 1-on-1 DM Threads
  const streakThreadId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(streakThreadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      isGroup: false,
      groupName: "",
      participantNamesById: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      kickedUserIds: [],
      blockedByUserIds: [],
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Hey!",
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [BOB_UID],
      talkStreakCount: 2,
      talkStreakLastMutualDay: "2026-11-23",
      lastMessageDayByUserId: { [ALICE_UID]: "2026-11-24" },
      createdAt: pastDate(4200),
      updatedAt: pastDate(4200),
    })
  );
  // Bob responds on 2026-11-24 -> mutual talk streak advances to 3!
  await assertSucceeds(
    bobDb.collection("directThreads").doc(streakThreadId).update({
      talkStreakCount: 3,
      talkStreakLastMutualDay: "2026-11-24",
      lastMessageDayByUserId: { [ALICE_UID]: "2026-11-24", [BOB_UID]: "2026-11-24" },
      updatedAt: pastDate(4100),
    })
  );
});








