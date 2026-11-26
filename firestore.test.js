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
    age13Verified: true,
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

  // Invalid sex or age < 13 denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      sex: "INVALID_SEX",
      updatedAt: pastDate(4400),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      age: 12,
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

  // Participant Alice CAN report a private message in her thread (with atomic reportRateLimits)
  const dmRepBatch = aliceDb.batch();
  dmRepBatch.set(aliceDb.collection("safetyReports").doc("rep_dm_1"), {
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
  });
  dmRepBatch.set(aliceDb.collection("reportRateLimits").doc(ALICE_UID), {
    userId: ALICE_UID,
    recentTargetTimestamps: { [BOB_UID]: Date.now() },
    lastTargetUserId: BOB_UID,
    lastReportId: "rep_dm_1",
    updatedAt: pastDate(2000),
  });
  await assertSucceeds(dmRepBatch.commit());

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

// 41. WHO VIEWED MY PROFILE (2 PTS / PRO), PRIORITY BREAK-IN CHIRPS (1-2 PTS), 24H DM SCROLLING BANNER (1 PT), ADMIN POINTS & LIFETIME PRO
test("Profile views, 2-pt viewer unlock, Priority Break-In Chirps, 24h DM scrolling banner, Admin points editing, and Lifetime Pro are enforced", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const charlieUid = "charlie_vox_41";
  const charlieDb = testEnv.authenticatedContext(charlieUid, { email: "charlie@example.com", email_verified: true }).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertSucceeds(charlieDb.collection("users").doc(charlieUid).set(validProfilePayload(charlieUid)));

  // 1) Admin can edit a user's points balance AND grant Lifetime Pro (never expires)
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 15,
      proMember: true,
      proLifetime: true,
      updatedAt: pastDate(6000),
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      pointsBalance: 10,
      updatedAt: pastDate(5900),
    })
  );

  // Regular user CANNOT grant themselves Lifetime Pro or edit their own points arbitrarily
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      proLifetime: true,
      updatedAt: pastDate(5800),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      pointsBalance: 999,
      updatedAt: pastDate(5750),
    })
  );

  // Lifetime Pro Alice CAN upload a Reel even with proExpiresAtMillis == 0!
  await assertSucceeds(
    aliceDb.collection("reels").doc("reel_lifetime_pro").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_vox",
      authorColorHex: "#0088FF",
      authorTier: "USER",
      caption: "Lifetime Pro Reel!",
      thumbnailBase64: "AA==",
      durationSec: 12,
      chunkCount: 1,
      mimeType: "video/mp4",
      likedByUserIds: [],
      commentCount: 0,
      shareCount: 0,
      createdAt: pastDate(5700),
      updatedAt: pastDate(5700),
    })
  );

  // 2) Who Viewed My Profile: Alice visits Bob's profile -> Bob can read/list, Charlie CANNOT read Bob's views
  const viewId = `${BOB_UID}_${ALICE_UID}`;
  await assertSucceeds(
    aliceDb.collection("profileViews").doc(viewId).set({
      targetUserId: BOB_UID,
      viewerId: ALICE_UID,
      viewerName: "Alice",
      viewerHandle: "alice_vox",
      viewerColorHex: "#0088FF",
      viewerPhotoBase64: "",
      viewerTier: "USER",
      viewerProMember: true,
      viewedAtMillis: Date.now() - 3000,
      viewedDayKey: "2026-11-25",
      updatedAt: pastDate(5600),
    })
  );
  // Self-view is rejected
  await assertFails(
    bobDb.collection("profileViews").doc(`${BOB_UID}_${BOB_UID}`).set({
      targetUserId: BOB_UID,
      viewerId: BOB_UID,
      viewerName: "Bob",
      viewerHandle: "bob_vox",
      viewerColorHex: "#0088FF",
      viewerPhotoBase64: "",
      viewerTier: "USER",
      viewerProMember: false,
      viewedAtMillis: Date.now() - 2000,
      viewedDayKey: "2026-11-25",
      updatedAt: pastDate(5500),
    })
  );
  await assertSucceeds(bobDb.collection("profileViews").doc(viewId).get());
  await assertFails(charlieDb.collection("profileViews").doc(viewId).get());

  // Bob spends 2 points to unlock Who Viewed My Profile for 24 hours (100% platform revenue)
  const unlockViewersBatch = bobDb.batch();
  unlockViewersBatch.update(bobDb.collection("users").doc(BOB_UID), {
    pointsBalance: 8,
    profileViewersUnlockedUntilMillis: Date.now() + 24 * 60 * 60 * 1000,
    updatedAt: pastDate(5400),
  });
  unlockViewersBatch.set(bobDb.collection("pointTransactions").doc("tx_unlock_viewers_1"), {
    type: "UNLOCK_PROFILE_VIEWERS",
    buyerId: BOB_UID,
    buyerName: "Bob",
    creatorId: "",
    creatorName: "",
    postId: "",
    pointsDelta: -2,
    grossCents: 200,
    creatorShareCents: 0,
    platformShareCents: 200,
    createdAt: pastDate(5400),
  });
  await assertSucceeds(unlockViewersBatch.commit());

  // 3) Priority Break-In Chirp (1-2 pts) & 24-Hour DM Scrolling Banner (1 pt)
  const dmId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(dmId).set({
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
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [BOB_UID],
      createdAt: pastDate(5300),
      updatedAt: pastDate(5300),
    })
  );

  // Alice opts in to Priority Chirps in this DM
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(dmId).update({
      priorityChirpOptInUserIds: [ALICE_UID],
      updatedAt: pastDate(5200),
    })
  );

  // Bob sends a 2-point Urgent Priority Break-In Chirp to Alice!
  const chirpBatch = bobDb.batch();
  chirpBatch.update(bobDb.collection("users").doc(BOB_UID), {
    pointsBalance: 6,
    updatedAt: pastDate(5100),
  });
  chirpBatch.update(bobDb.collection("directThreads").doc(dmId), {
    lastPriorityChirpAtMillis: Date.now() - 1000,
    lastPriorityChirpSenderId: BOB_UID,
    lastPriorityChirpSenderName: "Bob",
    lastPriorityChirpMessage: "Pick up ASAP — live show starting!",
    lastPriorityChirpPoints: 2,
    lastPriorityChirpAcknowledgedBy: [BOB_UID],
    lastMessageText: "🚨 Priority Chirp: Pick up ASAP — live show starting!",
    lastMessageSenderId: BOB_UID,
    unreadByUserIds: [ALICE_UID],
    updatedAt: pastDate(5100),
  });
  chirpBatch.set(bobDb.collection("directThreads").doc(dmId).collection("messages").doc("msg_chirp_1"), {
    threadId: dmId,
    participantIds: [ALICE_UID, BOB_UID],
    senderId: BOB_UID,
    senderName: "Bob",
    senderTier: "USER",
    text: "Pick up ASAP — live show starting!",
    messageType: "PRIORITY_CHIRP",
    createdAt: pastDate(5100),
  });
  chirpBatch.set(bobDb.collection("pointTransactions").doc("tx_priority_chirp_1"), {
    type: "PRIORITY_CHIRP",
    buyerId: BOB_UID,
    buyerName: "Bob",
    creatorId: ALICE_UID,
    creatorName: "Alice",
    postId: dmId,
    pointsDelta: -2,
    grossCents: 200,
    creatorShareCents: 0,
    platformShareCents: 200,
    createdAt: pastDate(5100),
  });
  await assertSucceeds(chirpBatch.commit());

  // Alice acknowledges the Priority Chirp
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(dmId).update({
      lastPriorityChirpAcknowledgedBy: [BOB_UID, ALICE_UID],
      unreadByUserIds: [],
      updatedAt: pastDate(5000),
    })
  );

  // Bob spends 1 point to set a 24-hour Scrolling Banner in Alice & Bob's DM
  const bannerBatch = bobDb.batch();
  bannerBatch.update(bobDb.collection("users").doc(BOB_UID), {
    pointsBalance: 5,
    updatedAt: pastDate(4900),
  });
  bannerBatch.update(bobDb.collection("directThreads").doc(dmId), {
    bannerText: "🎉 Happy Birthday Alice! Tuning in tonight at 8pm!",
    bannerAuthorId: BOB_UID,
    bannerAuthorName: "Bob",
    bannerColorHex: "#FF2E93",
    bannerExpiresAtMillis: Date.now() + 24 * 60 * 60 * 1000,
    updatedAt: pastDate(4900),
  });
  bannerBatch.set(bobDb.collection("pointTransactions").doc("tx_dm_banner_1"), {
    type: "DM_SCROLLING_BANNER",
    buyerId: BOB_UID,
    buyerName: "Bob",
    creatorId: "",
    creatorName: "",
    postId: dmId,
    pointsDelta: -1,
    grossCents: 100,
    creatorShareCents: 0,
    platformShareCents: 100,
    createdAt: pastDate(4900),
  });
  await assertSucceeds(bannerBatch.commit());

  // Cannot set a DM scrolling banner without paying 1 point in batch
  await assertFails(
    bobDb.collection("directThreads").doc(dmId).update({
      bannerText: "Free spoofed banner",
      bannerAuthorId: BOB_UID,
      bannerAuthorName: "Bob",
      bannerColorHex: "#00D4FF",
      bannerExpiresAtMillis: Date.now() + 24 * 60 * 60 * 1000,
      updatedAt: pastDate(4800),
    })
  );
});

// 42. 13+ AGE VERIFICATION FOR APP & PURCHASES + 5-STRIKE AUTOMATED REPORT ESCALATION & PRIVATE FILE NOTES
test("13+ age check gates Pro/Points purchases and 5-strike reports automatically escalate with private file notes and staff resolution", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const kidUid = "kid_under13_user";
  const kidDb = testEnv.authenticatedContext(kidUid).firestore();
  const teenUid = "teen_14_user";
  const teenDb = testEnv.authenticatedContext(teenUid).firestore();
  const ts = pastDate(10000);
  const currentYear = new Date().getFullYear();

  // Grant Support role
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Support Staff",
      staffTier: "SUPPORT",
      canEnterInvisible: false,
      addedByUid: ADMIN_UID,
      addedByName: "Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 1) Under-13 user CANNOT verify age13Verified, CANNOT set age = 12, and CANNOT buy Points or Pro
  await assertSucceeds(
    kidDb.collection("users").doc(kidUid).set(
      validProfilePayload(kidUid, { age13Verified: false, birthYear: 0, age: 0 })
    )
  );
  await assertFails(
    kidDb.collection("users").doc(kidUid).update({
      age13Verified: true,
      birthYear: currentYear - 10,
      updatedAt: pastDate(9000),
    })
  );
  await assertFails(
    kidDb.collection("users").doc(kidUid).update({
      age: 12,
      updatedAt: pastDate(8900),
    })
  );
  await assertFails(
    kidDb.collection("users").doc(kidUid).update({
      pointsBalance: 5,
      totalSpentCents: 500,
      updatedAt: pastDate(8800),
    })
  );
  await assertFails(
    kidDb.collection("users").doc(kidUid).update({
      proMember: true,
      proExpiresAtMillis: Date.now() + 86400000,
      totalSpentCents: 999,
      updatedAt: pastDate(8700),
    })
  );
  await assertFails(
    kidDb.collection("pointTransactions").doc("tx_kid_buy").set({
      type: "BUY_POINTS",
      buyerId: kidUid,
      buyerName: "Kid",
      creatorId: "",
      creatorName: "",
      postId: "",
      pointsDelta: 5,
      grossCents: 500,
      creatorShareCents: 0,
      platformShareCents: 0,
      createdAt: pastDate(8600),
    })
  );

  // 2) 14-year-old user CAN verify 13+, set age = 14, buy points, and activate Pro (but still CANNOT set adultVerified 18+)
  await assertSucceeds(
    teenDb.collection("users").doc(teenUid).set(
      validProfilePayload(teenUid, { age13Verified: false, birthYear: 0, age: 0 })
    )
  );
  await assertSucceeds(
    teenDb.collection("users").doc(teenUid).update({
      age13Verified: true,
      birthYear: currentYear - 14,
      age: 14,
      updatedAt: pastDate(8500),
    })
  );
  await assertFails(
    teenDb.collection("users").doc(teenUid).update({
      adultVerified: true,
      birthYear: currentYear - 14,
      updatedAt: pastDate(8400),
    })
  );
  const teenBuyBatch = teenDb.batch();
  teenBuyBatch.update(teenDb.collection("users").doc(teenUid), {
    pointsBalance: 10,
    totalSpentCents: 1000,
    updatedAt: pastDate(8300),
  });
  teenBuyBatch.set(teenDb.collection("pointTransactions").doc("tx_teen_buy"), {
    type: "BUY_POINTS",
    buyerId: teenUid,
    buyerName: "Teen",
    creatorId: "",
    creatorName: "",
    postId: "",
    pointsDelta: 10,
    grossCents: 1000,
    creatorShareCents: 0,
    platformShareCents: 0,
    createdAt: pastDate(8300),
  });
  await assertSucceeds(teenBuyBatch.commit());

  const teenProBatch = teenDb.batch();
  teenProBatch.update(teenDb.collection("users").doc(teenUid), {
    pointsBalance: 0,
    proMember: true,
    proExpiresAtMillis: Date.now() + 30 * 24 * 60 * 60 * 1000,
    updatedAt: pastDate(8200),
  });
  teenProBatch.set(teenDb.collection("pointTransactions").doc("tx_teen_pro"), {
    type: "PRO_MEMBERSHIP",
    buyerId: teenUid,
    buyerName: "Teen",
    creatorId: "",
    creatorName: "",
    postId: "",
    pointsDelta: -10,
    grossCents: 999,
    creatorShareCents: 0,
    platformShareCents: 999,
    createdAt: pastDate(8200),
  });
  await assertSucceeds(teenProBatch.commit());

  // 3) 5-Strike Automated Report & Investigation Escalation + Private File Notes
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, { displayName: "Bob" })));
  await assertSucceeds(aliceDb.collection("rooms").doc("room_mod_test").set(validRoomPayload(ALICE_UID)));

  const rep2Uid = "reporter_strike_2";
  const rep3Uid = "reporter_strike_3";
  const rep4Uid = "reporter_strike_4";
  const rep5Uid = "reporter_strike_5";
  const rep2Db = testEnv.authenticatedContext(rep2Uid).firestore();
  const rep3Db = testEnv.authenticatedContext(rep3Uid).firestore();
  const rep4Db = testEnv.authenticatedContext(rep4Uid).firestore();
  const rep5Db = testEnv.authenticatedContext(rep5Uid).firestore();

  const buildReport = (reportId, details, t, reporterId = ALICE_UID, reporterName = "Alice") => ({
    reporterId,
    reporterName,
    roomId: "room_mod_test",
    roomTitle: "Late Night Acoustic Jam",
    roomHostId: ALICE_UID,
    targetUserId: BOB_UID,
    targetName: "Bob",
    category: "HARASSMENT",
    details,
    status: "OPEN",
    resolutionNote: "",
    createdAt: t,
    updatedAt: t,
  });

  const buildNote = (reportId, strikeNum, actionTaken, reason, t, reporterId = ALICE_UID, reporterName = "Alice") => ({
    targetUserId: BOB_UID,
    reportId,
    strikeNumber: strikeNum,
    actionTaken,
    category: "HARASSMENT",
    reason,
    reporterId,
    reporterName,
    actorRole: "REPORTER",
    createdAt: t,
  });

  const buildRateLimit = (uid, reportId, t) => ({
    userId: uid,
    recentTargetTimestamps: { [BOB_UID]: Date.now() },
    lastTargetUserId: BOB_UID,
    lastReportId: reportId,
    updatedAt: t,
  });

  // Spoofing check: Alice CANNOT suspend Bob on Strike 1!
  const spoofStrike1 = aliceDb.batch();
  spoofStrike1.set(aliceDb.collection("safetyReports").doc("rep_spoof_1"), buildReport("rep_spoof_1", "Spoofed suspension", pastDate(7900)));
  spoofStrike1.set(aliceDb.collection("reportRateLimits").doc(ALICE_UID), buildRateLimit(ALICE_UID, "rep_spoof_1", pastDate(7900)));
  spoofStrike1.set(aliceDb.collection("userModerationFiles").doc(BOB_UID), {
    userId: BOB_UID,
    targetName: "Bob",
    reportCount: 1,
    warningCount: 0,
    investigationActive: true,
    investigationStatus: "INVESTIGATING",
    suspendedUntilMillis: Date.now() + 3600000,
    suspendedUntilInvestigationResolved: false,
    suspensionReason: "Illegal early suspension",
    lastWarningMessage: "",
    lastWarningAtMillis: 0,
    lastWarningAcknowledgedAtMillis: 0,
    lastReportId: "rep_spoof_1",
    updatedAt: pastDate(7900),
  });
  await assertFails(spoofStrike1.commit());

  // STRIKE 1: Investigation started, NO suspension, report note enters file; Bob can still use the app
  const b1 = aliceDb.batch();
  b1.set(aliceDb.collection("safetyReports").doc("rep_1"), buildReport("rep_1", "1st report issue", pastDate(7800)));
  b1.set(aliceDb.collection("reportRateLimits").doc(ALICE_UID), buildRateLimit(ALICE_UID, "rep_1", pastDate(7800)));
  b1.set(aliceDb.collection("userModerationFiles").doc(BOB_UID), {
    userId: BOB_UID,
    targetName: "Bob",
    reportCount: 1,
    warningCount: 0,
    investigationActive: true,
    investigationStatus: "INVESTIGATING",
    suspendedUntilMillis: 0,
    suspendedUntilInvestigationResolved: false,
    suspensionReason: "",
    lastWarningMessage: "",
    lastWarningAtMillis: 0,
    lastWarningAcknowledgedAtMillis: 0,
    lastReportId: "rep_1",
    updatedAt: pastDate(7800),
  });
  b1.set(
    aliceDb.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_1"),
    buildNote("rep_1", 1, "INVESTIGATION_STARTED", "1st report issue", pastDate(7800))
  );
  await assertSucceeds(b1.commit());

  // Bob can still create a room after Strike 1 (not suspended)
  await assertSucceeds(bobDb.collection("rooms").doc("bob_room_after_s1").set(validRoomPayload(BOB_UID)));

  // Regular users (Alice & Bob) CANNOT read private file notes — only Support & Admin can!
  await assertFails(aliceDb.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_1").get());
  await assertFails(bobDb.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_1").get());
  await assertSucceeds(supportDb.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_1").get());
  await assertSucceeds(adminDb.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_1").get());

  // STRIKE 2: Warning issued, NO suspension, report note enters file
  const b2 = rep2Db.batch();
  b2.set(rep2Db.collection("safetyReports").doc("rep_2"), buildReport("rep_2", "2nd report issue", pastDate(7500), rep2Uid, "Rep2"));
  b2.set(rep2Db.collection("reportRateLimits").doc(rep2Uid), buildRateLimit(rep2Uid, "rep_2", pastDate(7500)));
  b2.update(rep2Db.collection("userModerationFiles").doc(BOB_UID), {
    reportCount: 2,
    warningCount: 1,
    investigationActive: true,
    investigationStatus: "WARNING_ISSUED",
    lastWarningMessage: "Formal warning: 2nd safety report logged on your account.",
    lastWarningAtMillis: Date.now() - 5000,
    lastReportId: "rep_2",
    updatedAt: pastDate(7500),
  });
  b2.set(
    rep2Db.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_2"),
    buildNote("rep_2", 2, "WARNING_ISSUED", "2nd report issue", pastDate(7500), rep2Uid, "Rep2")
  );
  await assertSucceeds(b2.commit());

  // Bob acknowledges his Strike 2 warning
  await assertSucceeds(
    bobDb.collection("userModerationFiles").doc(BOB_UID).update({
      lastWarningAcknowledgedAtMillis: Date.now(),
      updatedAt: pastDate(7400),
    })
  );

  // STRIKE 3: Automatic 1-hour suspension, report note enters file
  const b3 = rep3Db.batch();
  b3.set(rep3Db.collection("safetyReports").doc("rep_3"), buildReport("rep_3", "3rd report issue", pastDate(7200), rep3Uid, "Rep3"));
  b3.set(rep3Db.collection("reportRateLimits").doc(rep3Uid), buildRateLimit(rep3Uid, "rep_3", pastDate(7200)));
  b3.update(rep3Db.collection("userModerationFiles").doc(BOB_UID), {
    reportCount: 3,
    investigationActive: true,
    investigationStatus: "SUSPENDED_1H",
    suspendedUntilMillis: Date.now() + 3600000,
    suspensionReason: "3rd report: Automatic 1-hour suspension.",
    lastReportId: "rep_3",
    updatedAt: pastDate(7200),
  });
  b3.set(
    rep3Db.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_3"),
    buildNote("rep_3", 3, "SUSPENDED_1H", "3rd report issue", pastDate(7200), rep3Uid, "Rep3")
  );
  await assertSucceeds(b3.commit());

  // Bob is NOW blocked by Firestore rules from creating a room during 1h suspension!
  await assertFails(bobDb.collection("rooms").doc("bob_room_blocked_s3").set(validRoomPayload(BOB_UID)));

  // STRIKE 4: Automatic 2-hour suspension, report note enters file
  const b4 = rep4Db.batch();
  b4.set(rep4Db.collection("safetyReports").doc("rep_4"), buildReport("rep_4", "4th report issue", pastDate(7000), rep4Uid, "Rep4"));
  b4.set(rep4Db.collection("reportRateLimits").doc(rep4Uid), buildRateLimit(rep4Uid, "rep_4", pastDate(7000)));
  b4.update(rep4Db.collection("userModerationFiles").doc(BOB_UID), {
    reportCount: 4,
    investigationActive: true,
    investigationStatus: "SUSPENDED_2H",
    suspendedUntilMillis: Date.now() + 7200000,
    suspensionReason: "4th report: Automatic 2-hour suspension.",
    lastReportId: "rep_4",
    updatedAt: pastDate(7000),
  });
  b4.set(
    rep4Db.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_4"),
    buildNote("rep_4", 4, "SUSPENDED_2H", "4th report issue", pastDate(7000), rep4Uid, "Rep4")
  );
  await assertSucceeds(b4.commit());

  // STRIKE 5: Suspended until Support or Admin resolves investigation, report note enters file
  const b5 = rep5Db.batch();
  b5.set(rep5Db.collection("safetyReports").doc("rep_5"), buildReport("rep_5", "5th report issue", pastDate(6800), rep5Uid, "Rep5"));
  b5.set(rep5Db.collection("reportRateLimits").doc(rep5Uid), buildRateLimit(rep5Uid, "rep_5", pastDate(6800)));
  b5.update(rep5Db.collection("userModerationFiles").doc(BOB_UID), {
    reportCount: 5,
    investigationActive: true,
    investigationStatus: "SUSPENDED_PENDING_REVIEW",
    suspendedUntilInvestigationResolved: true,
    suspensionReason: "5th report: Suspended until investigation by Support or Admin is resolved.",
    lastReportId: "rep_5",
    updatedAt: pastDate(6800),
  });
  b5.set(
    rep5Db.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("rep_5"),
    buildNote("rep_5", 5, "SUSPENDED_PENDING_REVIEW", "5th report issue", pastDate(6800), rep5Uid, "Rep5")
  );
  await assertSucceeds(b5.commit());

  // Bob CANNOT lift his own Strike 5 suspension
  await assertFails(
    bobDb.collection("userModerationFiles").doc(BOB_UID).update({
      suspendedUntilInvestigationResolved: false,
      suspendedUntilMillis: 0,
      updatedAt: pastDate(6600),
    })
  );

  // Support resolves the investigation, lifts the suspension, and appends a resolution note to Bob's file!
  const resolveBatch = supportDb.batch();
  resolveBatch.update(supportDb.collection("userModerationFiles").doc(BOB_UID), {
    investigationActive: false,
    investigationStatus: "RESOLVED",
    suspendedUntilMillis: 0,
    suspendedUntilInvestigationResolved: false,
    suspensionReason: "",
    lastResolvedById: SUPPORT_UID,
    lastResolvedByName: "Support Staff",
    lastResolutionNote: "Reviewed logs and resolved investigation.",
    updatedAt: pastDate(6400),
  });
  resolveBatch.set(supportDb.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("resolve_1"), {
    targetUserId: BOB_UID,
    reportId: "",
    strikeNumber: 5,
    actionTaken: "INVESTIGATION_RESOLVED",
    category: "RESOLUTION",
    reason: "Reviewed logs and resolved investigation.",
    reporterId: SUPPORT_UID,
    reporterName: "Support Staff",
    actorRole: "SUPPORT",
    createdAt: pastDate(6400),
  });
  await assertSucceeds(resolveBatch.commit());

  // After Support resolves the investigation, Bob CAN use the app again!
  await assertSucceeds(bobDb.collection("rooms").doc("bob_room_after_resolve").set(validRoomPayload(BOB_UID)));
});

// 43. REPORT RATE LIMIT (3 USERS 1x/HR), SUPPORT SUSPENSION REQUESTS (PENDING ADMIN REVIEW),
//     24H ROOM BOUNCE LIST & UNBOUNCE, ROOM BAN, 4-DIGIT PIN LOCK, & ROOM ADMIN INVISIBLE MODE
test("Report rate limit, Support suspension request pending Admin review, room bounce/unbounce, ban, 4-digit PIN lock, and Room Admin invisible mode", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const supportDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(9000);

  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Support Staff",
      staffTier: "SUPPORT",
      canEnterInvisible: false,
      addedByUid: ADMIN_UID,
      addedByName: "Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, { displayName: "Bob" })));
  await assertSucceeds(aliceDb.collection("rooms").doc("room_ctrl_1").set(validRoomPayload(ALICE_UID)));

  // 1) REPORT RATE LIMIT: 3 different targets 1 time each within 1 hour
  const mkRep = (repId, targetUid, targetName, t) => ({
    reporterId: ALICE_UID,
    reporterName: "Alice",
    roomId: "room_ctrl_1",
    roomTitle: "Late Night Acoustic Jam",
    roomHostId: ALICE_UID,
    targetUserId: targetUid,
    targetName,
    category: "HARASSMENT",
    details: "Rate limit check",
    status: "OPEN",
    resolutionNote: "",
    createdAt: t,
    updatedAt: t,
  });

  // Bypass without reportRateLimits DENIED
  await assertFails(aliceDb.collection("safetyReports").doc("rl_bypass").set(mkRep("rl_bypass", "t1", "T1", pastDate(8500))));

  const t1Now = Date.now();
  const r1 = aliceDb.batch();
  r1.set(aliceDb.collection("safetyReports").doc("rl_1"), mkRep("rl_1", "t1", "T1", pastDate(8400)));
  r1.set(aliceDb.collection("reportRateLimits").doc(ALICE_UID), {
    userId: ALICE_UID,
    recentTargetTimestamps: { t1: t1Now },
    lastTargetUserId: "t1",
    lastReportId: "rl_1",
    updatedAt: pastDate(8400),
  });
  await assertSucceeds(r1.commit());

  // Duplicate report against same user t1 within 1h DENIED
  const dup = aliceDb.batch();
  dup.set(aliceDb.collection("safetyReports").doc("rl_dup"), mkRep("rl_dup", "t1", "T1", pastDate(8300)));
  dup.update(aliceDb.collection("reportRateLimits").doc(ALICE_UID), {
    recentTargetTimestamps: { t1: Date.now() },
    lastTargetUserId: "t1",
    lastReportId: "rl_dup",
    updatedAt: pastDate(8300),
  });
  await assertFails(dup.commit());

  // 2nd distinct target t2 ALLOWED
  const t2Now = Date.now();
  const r2 = aliceDb.batch();
  r2.set(aliceDb.collection("safetyReports").doc("rl_2"), mkRep("rl_2", "t2", "T2", pastDate(8200)));
  r2.update(aliceDb.collection("reportRateLimits").doc(ALICE_UID), {
    recentTargetTimestamps: { t1: t1Now, t2: t2Now },
    lastTargetUserId: "t2",
    lastReportId: "rl_2",
    updatedAt: pastDate(8200),
  });
  await assertSucceeds(r2.commit());

  // 3rd distinct target t3 ALLOWED
  const t3Now = Date.now();
  const r3 = aliceDb.batch();
  r3.set(aliceDb.collection("safetyReports").doc("rl_3"), mkRep("rl_3", "t3", "T3", pastDate(8100)));
  r3.update(aliceDb.collection("reportRateLimits").doc(ALICE_UID), {
    recentTargetTimestamps: { t1: t1Now, t2: t2Now, t3: t3Now },
    lastTargetUserId: "t3",
    lastReportId: "rl_3",
    updatedAt: pastDate(8100),
  });
  await assertSucceeds(r3.commit());

  // 4th distinct target t4 within 1h DENIED (even if attempting to drop t1!)
  const r4Drop = aliceDb.batch();
  r4Drop.set(aliceDb.collection("safetyReports").doc("rl_4"), mkRep("rl_4", "t4", "T4", pastDate(8000)));
  r4Drop.update(aliceDb.collection("reportRateLimits").doc(ALICE_UID), {
    recentTargetTimestamps: { t2: t2Now, t3: t3Now, t4: Date.now() },
    lastTargetUserId: "t4",
    lastReportId: "rl_4",
    updatedAt: pastDate(8000),
  });
  await assertFails(r4Drop.commit());

  // 2) SUPPORT SUSPENSION REQUEST -> IMMEDIATELY SUSPENDS TARGET UNTIL ADMIN REVIEWS
  // Regular user CANNOT create a suspensionRequest
  await assertFails(
    aliceDb.collection("suspensionRequests").doc("sr_spoof").set({
      targetUserId: BOB_UID,
      targetName: "Bob",
      requestedById: ALICE_UID,
      requestedByName: "Alice",
      reason: "Unauthorized request",
      roomId: "room_ctrl_1",
      status: "PENDING",
      reviewedByAdminId: "",
      reviewedByAdminName: "",
      adminDecisionNote: "",
      createdAt: pastDate(7900),
      updatedAt: pastDate(7900),
    })
  );

  // Support submits suspension request with mandatory reason + immediately suspends Bob pending Admin review
  const srBatch = supportDb.batch();
  srBatch.set(supportDb.collection("suspensionRequests").doc("sr_1"), {
    targetUserId: BOB_UID,
    targetName: "Bob",
    requestedById: SUPPORT_UID,
    requestedByName: "Support Staff",
    reason: "Repeated harassment across live rooms.",
    roomId: "room_ctrl_1",
    status: "PENDING",
    reviewedByAdminId: "",
    reviewedByAdminName: "",
    adminDecisionNote: "",
    createdAt: pastDate(7800),
    updatedAt: pastDate(7800),
  });
  srBatch.set(supportDb.collection("userModerationFiles").doc(BOB_UID), {
    userId: BOB_UID,
    targetName: "Bob",
    reportCount: 0,
    warningCount: 0,
    investigationActive: true,
    investigationStatus: "SUSPENDED_PENDING_ADMIN_REVIEW",
    suspendedUntilMillis: 0,
    suspendedUntilInvestigationResolved: true,
    pendingAdminReview: true,
    pendingSuspensionRequestId: "sr_1",
    suspensionReason: "Support requested suspension pending Admin review: Repeated harassment across live rooms.",
    lastWarningMessage: "",
    lastWarningAtMillis: 0,
    lastWarningAcknowledgedAtMillis: 0,
    lastReportId: "",
    updatedAt: pastDate(7800),
  });
  srBatch.set(supportDb.collection("userModerationFiles").doc(BOB_UID).collection("notes").doc("sr_1"), {
    targetUserId: BOB_UID,
    reportId: "sr_1",
    strikeNumber: 0,
    actionTaken: "SUPPORT_SUSPENSION_REQUESTED",
    category: "SUSPENSION_REQUEST",
    reason: "Repeated harassment across live rooms.",
    reporterId: SUPPORT_UID,
    reporterName: "Support Staff",
    actorRole: "SUPPORT",
    createdAt: pastDate(7800),
  });
  await assertSucceeds(srBatch.commit());

  // Bob is immediately suspended (cannot create a room)
  await assertFails(bobDb.collection("rooms").doc("bob_suspended_try").set(validRoomPayload(BOB_UID)));

  // Support CANNOT lift a pending Admin review suspension!
  await assertFails(
    supportDb.collection("userModerationFiles").doc(BOB_UID).update({
      pendingAdminReview: false,
      suspendedUntilInvestigationResolved: false,
      investigationActive: false,
      investigationStatus: "RESOLVED",
      updatedAt: pastDate(7700),
    })
  );
  // Support CANNOT approve/dismiss suspensionRequests (Admin ONLY!)
  await assertFails(
    supportDb.collection("suspensionRequests").doc("sr_1").update({
      status: "DISMISSED_LIFTED",
      reviewedByAdminId: SUPPORT_UID,
      reviewedByAdminName: "Support Staff",
      adminDecisionNote: "Trying to self-approve",
      updatedAt: pastDate(7700),
    })
  );

  // Admin reviews & lifts the suspension request
  const adminRev = adminDb.batch();
  adminRev.update(adminDb.collection("suspensionRequests").doc("sr_1"), {
    status: "DISMISSED_LIFTED",
    reviewedByAdminId: ADMIN_UID,
    reviewedByAdminName: "App Admin",
    adminDecisionNote: "Reviewed and cleared after warning.",
    updatedAt: pastDate(7600),
  });
  adminRev.update(adminDb.collection("userModerationFiles").doc(BOB_UID), {
    pendingAdminReview: false,
    pendingSuspensionRequestId: "",
    suspendedUntilInvestigationResolved: false,
    suspendedUntilMillis: 0,
    investigationActive: false,
    investigationStatus: "RESOLVED",
    suspensionReason: "",
    lastResolvedById: ADMIN_UID,
    lastResolvedByName: "App Admin",
    lastResolutionNote: "Reviewed and cleared after warning.",
    updatedAt: pastDate(7600),
  });
  await assertSucceeds(adminRev.commit());

  // 3) 24-HOUR ROOM BOUNCE LIST & ROOM ADMIN UNBOUNCE
  const bounceUntil = Date.now() + 86400000;
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ctrl_1").update({
      bouncedUntilByUserId: { [BOB_UID]: bounceUntil },
      bouncedNamesByUserId: { [BOB_UID]: "Bob" },
      updatedAt: pastDate(7500),
    })
  );

  const bobParticipantPayload = (extra = {}) => ({
    userId: BOB_UID,
    roomId: "room_ctrl_1",
    displayName: "Bob",
    avatarColorHex: "#1877F2",
    role: "LISTENER",
    staffTier: "USER",
    invisible: false,
    micActive: false,
    camActive: false,
    handRaised: false,
    mutedByMod: false,
    speakingLevel: 0.0,
    joinedAt: pastDate(7400),
    updatedAt: pastDate(7400),
    ...extra,
  });

  // Bounced Bob CANNOT enter the room for 24h
  await assertFails(
    bobDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).set(bobParticipantPayload())
  );

  // Room Admin Alice removes Bob from the Bounce List
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ctrl_1").update({
      bouncedUntilByUserId: {},
      bouncedNamesByUserId: {},
      updatedAt: pastDate(7300),
    })
  );

  // 4) ROOM ADMIN 4-DIGIT PIN CODE LOCK
  // Invalid 3-digit PIN DENIED
  await assertFails(
    aliceDb.collection("rooms").doc("room_ctrl_1").update({
      pinLocked: true,
      roomPinCode: "123",
      updatedAt: pastDate(7250),
    })
  );
  // Valid 4-digit PIN ALLOWED
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ctrl_1").update({
      pinLocked: true,
      roomPinCode: "4829",
      updatedAt: pastDate(7200),
    })
  );

  // Bob WITHOUT PIN or with WRONG PIN is DENIED
  await assertFails(
    bobDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).set(bobParticipantPayload())
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).set(bobParticipantPayload({ enteredPin: "9999" }))
  );

  // Bob WITH CORRECT 4-digit PIN is ALLOWED
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).set(bobParticipantPayload({ enteredPin: "4829" }))
  );

  // 5) ROOM ADMIN INVISIBLE MODE (Room Admin ALLOWED; regular listener DENIED)
  // Regular listener Bob CANNOT turn invisible
  await assertFails(
    bobDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).update({
      invisible: true,
      updatedAt: pastDate(7100),
    })
  );
  // Room Admin Alice CAN enter & toggle invisible in her own room (bypassing PIN too)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(ALICE_UID).set({
      userId: ALICE_UID,
      roomId: "room_ctrl_1",
      displayName: "Alice",
      avatarColorHex: "#9333EA",
      role: "HOST",
      staffTier: "USER",
      invisible: true,
      micActive: false,
      camActive: false,
      handRaised: false,
      mutedByMod: false,
      speakingLevel: 0.0,
      joinedAt: pastDate(7050),
      updatedAt: pastDate(7050),
    })
  );

  // 6) ROOM ADMIN BAN & UNBAN
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ctrl_1").update({
      bannedUserIds: [BOB_UID],
      bannedNamesByUserId: { [BOB_UID]: "Bob" },
      updatedAt: pastDate(7000),
    })
  );
  await assertSucceeds(aliceDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).delete());
  // Banned Bob CANNOT re-enter even with correct PIN
  await assertFails(
    bobDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).set(bobParticipantPayload({ enteredPin: "4829" }))
  );
  // Room Admin unbans Bob
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ctrl_1").update({
      bannedUserIds: [],
      bannedNamesByUserId: {},
      updatedAt: pastDate(6900),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_ctrl_1").collection("participants").doc(BOB_UID).set(bobParticipantPayload({ enteredPin: "4829" }))
  );
});

// 44. REFUNDS + AUTO-UNSUBSCRIBE, APP PERK GRANTS + 30/5D ALERT, GROUP DM HIDE NAME & GROUP DM ADMIN (MUTE/BOUNCE/BAN)
test("Admin & Support refunds revoke memberships + report to Admins; perk grants + 30/5d alert; Group DM hide name & admin mute/bounce/ban", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const supportUid = "support_staff_refunds";
  const supportDb = testEnv.authenticatedContext(supportUid).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieUid = "charlie_uid";
  const charlieDb = testEnv.authenticatedContext(charlieUid).firestore();

  const ts = pastDate(10000);
  const future30d = Date.now() + 30 * 24 * 60 * 60 * 1000;

  // Seed users, directory, Support staff role, active Pro + Creator Sub + Transaction
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffRoles").doc(supportUid).set({
      userId: supportUid,
      email: "support@vox.test",
      displayName: "Support Rep",
      staffTier: "SUPPORT",
      canEnterInvisible: false,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      proMember: true,
      proExpiresAtMillis: future30d,
      proLifetime: false,
      pointsBalance: 10,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(BOB_UID).set({
      ...validProfilePayload(BOB_UID),
      displayName: "Bob",
      handle: "bob_vox",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(charlieUid).set({
      ...validProfilePayload(charlieUid),
      displayName: "Charlie",
      handle: "charlie_vox",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      avatarColorHex: "#0088FF",
      staffTier: "USER",
      proMember: true,
      proExpiresAtMillis: future30d,
      updatedAt: ts,
    });
    await db.collection("creatorSubscriptions").doc(`${ALICE_UID}_${BOB_UID}`).set({
      subscriberId: ALICE_UID,
      creatorId: BOB_UID,
      creatorName: "Bob",
      priceCents: 1500,
      creatorShareCents: 0,
      platformShareCents: 1500,
      active: true,
      expiresAtMillis: future30d,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("pointTransactions").doc("tx_pro_1").set({
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
      createdAt: ts,
    });
  });

  // 1) SUPPORT ISSUES VOX PRO REFUND -> REVOKES PRO MEMBERSHIP + WRITES REFUND REPORT + NOTIFIES APP ADMINS
  // Regular user Bob CANNOT create a refund report or revoke Alice's Pro
  await assertFails(
    bobDb.collection("refundReports").doc("ref_bad").set({
      targetUserId: ALICE_UID,
      targetName: "Alice",
      refundType: "VOX_PRO",
      itemLabel: "VOX Pro Membership",
      amountCents: 999,
      transactionId: "tx_pro_1",
      subscriptionId: "",
      membershipRevoked: true,
      reason: "Fake refund",
      issuedById: BOB_UID,
      issuedByName: "Bob",
      issuedByTier: "SUPPORT",
      reportedToAdmins: true,
      acknowledgedByAdmin: false,
      acknowledgedByAdminName: "",
      createdAt: pastDate(9000),
      updatedAt: pastDate(9000),
    })
  );

  // Support revokes Alice's Pro membership on users & userDirectory, marks tx refunded, logs refundReport, notifies Admins
  await assertSucceeds(
    supportDb.collection("users").doc(ALICE_UID).update({
      proMember: false,
      proExpiresAtMillis: 0,
      proLifetime: false,
      updatedAt: pastDate(8900),
    })
  );
  await assertSucceeds(
    supportDb.collection("userDirectory").doc(ALICE_UID).update({
      proMember: false,
      proExpiresAtMillis: 0,
      updatedAt: pastDate(8900),
    })
  );
  await assertSucceeds(
    supportDb.collection("pointTransactions").doc("tx_pro_1").update({
      refunded: true,
      refundedAtMillis: Date.now(),
      refundedById: supportUid,
      refundedByName: "Support Rep",
      refundReason: "Duplicate charge requested by user",
    })
  );
  await assertSucceeds(
    supportDb.collection("refundReports").doc("ref_support_1").set({
      targetUserId: ALICE_UID,
      targetName: "Alice",
      refundType: "VOX_PRO",
      itemLabel: "VOX Pro Membership",
      amountCents: 999,
      transactionId: "tx_pro_1",
      subscriptionId: "",
      membershipRevoked: true,
      reason: "Duplicate charge requested by user",
      issuedById: supportUid,
      issuedByName: "Support Rep",
      issuedByTier: "SUPPORT",
      reportedToAdmins: true,
      acknowledgedByAdmin: false,
      acknowledgedByAdminName: "",
      createdAt: pastDate(8800),
      updatedAt: pastDate(8800),
    })
  );
  await assertSucceeds(
    supportDb.collection("adminNotifications").doc("notif_refund_1").set({
      type: "SUPPORT_REFUND_REPORT",
      title: "Support Refund Issued",
      message: "Support Rep refunded VOX Pro Membership ($9.99) for Alice. Membership revoked.",
      createdById: supportUid,
      createdByName: "Support Rep",
      targetUserId: ALICE_UID,
      targetName: "Alice",
      referenceId: "ref_support_1",
      countValue: 1,
      read: false,
      createdAt: pastDate(8750),
      updatedAt: pastDate(8750),
    })
  );

  // Support CANNOT read adminNotifications (Admin-only!), while Admin CAN read and mark read + acknowledge refundReport
  await assertFails(supportDb.collection("adminNotifications").doc("notif_refund_1").get());
  await assertSucceeds(adminDb.collection("adminNotifications").doc("notif_refund_1").get());
  await assertSucceeds(
    adminDb.collection("adminNotifications").doc("notif_refund_1").update({
      read: true,
      updatedAt: pastDate(8700),
    })
  );
  await assertSucceeds(
    adminDb.collection("refundReports").doc("ref_support_1").update({
      acknowledgedByAdmin: true,
      acknowledgedByAdminName: "App Admin",
      updatedAt: pastDate(8650),
    })
  );

  // Admin issues a Creator Subscription refund -> immediately revokes subscription (active=false, expiresAtMillis=0, refunded=true)
  await assertSucceeds(
    adminDb.collection("creatorSubscriptions").doc(`${ALICE_UID}_${BOB_UID}`).update({
      active: false,
      expiresAtMillis: 0,
      refunded: true,
      updatedAt: pastDate(8600),
    })
  );

  // 2) ADMIN & SUPPORT GRANT ANY APP PERK + 30-PERKS-IN-5-DAYS ADMIN NOTIFICATION
  // Regular user Bob CANNOT grant perks
  await assertFails(
    bobDb.collection("perkGrants").doc("perk_bad").set({
      targetUserId: ALICE_UID,
      targetName: "Alice",
      perkType: "SPOTLIGHT_FRAME_24H",
      perkLabel: "24h Spotlight Frame",
      perkDetail: "#00D4FF",
      reason: "Unauthorized",
      grantedById: BOB_UID,
      grantedByName: "Bob",
      grantedByTier: "SUPPORT",
      createdAtMillis: Date.now(),
      createdAt: pastDate(8500),
    })
  );

  // Support grants Spotlight Frame + Custom PTT Theme to Alice & logs perkGrant + triggers 30-in-5-day alert to Admins
  await assertSucceeds(
    supportDb.collection("users").doc(ALICE_UID).update({
      spotlightFrameUntilMillis: Date.now() + 24 * 60 * 60 * 1000,
      spotlightFrameColorHex: "#00D4FF",
      ownedPttThemes: ["CLASSIC", "NEON_MAGENTA"],
      selectedPttTheme: "NEON_MAGENTA",
      updatedAt: pastDate(8450),
    })
  );
  await assertSucceeds(
    supportDb.collection("userDirectory").doc(ALICE_UID).update({
      spotlightFrameUntilMillis: Date.now() + 24 * 60 * 60 * 1000,
      spotlightFrameColorHex: "#00D4FF",
      updatedAt: pastDate(8450),
    })
  );
  await assertSucceeds(
    supportDb.collection("perkGrants").doc("perk_support_1").set({
      targetUserId: ALICE_UID,
      targetName: "Alice",
      perkType: "SPOTLIGHT_FRAME_24H",
      perkLabel: "24h Spotlight Frame",
      perkDetail: "#00D4FF",
      reason: "Community event winner",
      grantedById: supportUid,
      grantedByName: "Support Rep",
      grantedByTier: "SUPPORT",
      createdAtMillis: Date.now(),
      createdAt: pastDate(8400),
    })
  );
  await assertSucceeds(
    supportDb.collection("adminNotifications").doc("notif_perks_30").set({
      type: "SUPPORT_PERK_THRESHOLD_30_IN_5D",
      title: "Support Perk Limit Reached (30 in 5 Days)",
      message: "Support Staff have granted 30 app perks over the past 5 days.",
      createdById: supportUid,
      createdByName: "Support Rep",
      targetUserId: ALICE_UID,
      targetName: "Alice",
      referenceId: "perk_support_1",
      countValue: 30,
      read: false,
      createdAt: pastDate(8350),
      updatedAt: pastDate(8350),
    })
  );

  // 3) GROUP DM: CREATOR IS GROUP DM ADMIN (MUTE, BOUNCE, BAN) + HIDE NAME MESSAGES
  const groupId = "group_dm_admin_test";
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(groupId).set({
      participantIds: [ALICE_UID, BOB_UID, charlieUid],
      isGroup: true,
      groupName: "Friday Squad",
      groupAdminId: ALICE_UID,
      participantNamesById: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob", [charlieUid]: "Charlie" },
      kickedUserIds: [],
      mutedUserIds: [],
      bouncedUntilByUserId: {},
      bouncedNamesByUserId: {},
      bannedUserIds: [],
      bannedNamesByUserId: {},
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Welcome to Friday Squad",
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [BOB_UID, charlieUid],
      createdAt: pastDate(8200),
      updatedAt: pastDate(8200),
    })
  );

  // Bob sends a hidden-name message in the Group DM -> ALLOWED (and still has senderId so it can be reported!)
  await assertSucceeds(
    bobDb.collection("directThreads").doc(groupId).collection("messages").doc("msg_hidden_1").set({
      threadId: groupId,
      participantIds: [ALICE_UID, BOB_UID, charlieUid],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Mystery message from hidden member",
      hideSenderName: true,
      createdAt: pastDate(8100),
    })
  );

  // Non-admin member Bob CANNOT mute/bounce/ban Charlie
  await assertFails(
    bobDb.collection("directThreads").doc(groupId).update({
      mutedUserIds: [charlieUid],
      updatedAt: pastDate(8050),
    })
  );

  // Group DM Admin Alice MUTES Bob -> Bob DENIED sending messages
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(groupId).update({
      mutedUserIds: [BOB_UID],
      updatedAt: pastDate(8000),
    })
  );
  await assertFails(
    bobDb.collection("directThreads").doc(groupId).collection("messages").doc("msg_bob_muted").set({
      threadId: groupId,
      participantIds: [ALICE_UID, BOB_UID, charlieUid],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Trying to talk while muted",
      createdAt: pastDate(7950),
    })
  );

  // Alice unmutes Bob & BOUNCES Bob for 24 hours -> Bob DENIED sending messages
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(groupId).update({
      mutedUserIds: [],
      bouncedUntilByUserId: { [BOB_UID]: Date.now() + 24 * 60 * 60 * 1000 },
      bouncedNamesByUserId: { [BOB_UID]: "Bob" },
      updatedAt: pastDate(7900),
    })
  );
  await assertFails(
    bobDb.collection("directThreads").doc(groupId).collection("messages").doc("msg_bob_bounced").set({
      threadId: groupId,
      participantIds: [ALICE_UID, BOB_UID, charlieUid],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Trying to talk while bounced",
      createdAt: pastDate(7850),
    })
  );

  // Alice removes bounce & BANS Bob -> Bob DENIED sending messages
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(groupId).update({
      bouncedUntilByUserId: {},
      bouncedNamesByUserId: {},
      bannedUserIds: [BOB_UID],
      bannedNamesByUserId: { [BOB_UID]: "Bob" },
      updatedAt: pastDate(7800),
    })
  );
  await assertFails(
    bobDb.collection("directThreads").doc(groupId).collection("messages").doc("msg_bob_banned").set({
      threadId: groupId,
      participantIds: [ALICE_UID, BOB_UID, charlieUid],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Trying to talk while banned",
      createdAt: pastDate(7750),
    })
  );

  // Alice unbans Bob -> Bob ALLOWED again
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(groupId).update({
      bannedUserIds: [],
      bannedNamesByUserId: {},
      updatedAt: pastDate(7700),
    })
  );
  await assertSucceeds(
    bobDb.collection("directThreads").doc(groupId).collection("messages").doc("msg_bob_unbanned").set({
      threadId: groupId,
      participantIds: [ALICE_UID, BOB_UID, charlieUid],
      senderId: BOB_UID,
      senderName: "Bob",
      senderTier: "USER",
      text: "Back in the group!",
      createdAt: pastDate(7650),
    })
  );
});

test("45. Group DM Support Staff, Tiered Caps (7/15/25/50), and >15-Member PTT Speaker Selection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  const charlieUid = "charlie_uid_45";
  const charlieDb = testEnv.authenticatedContext(charlieUid, { email: "charlie@example.com" }).firestore();

  const makeParticipants = (n) => {
    const ids = [ALICE_UID, BOB_UID, charlieUid];
    for (let i = 4; i <= n; i++) ids.push(`member_${i}_45`);
    return ids.slice(0, n);
  };

  const makeUser = (uid, name, handle, extra = {}) => ({
    userId: uid,
    displayName: name,
    handle,
    bio: "",
    avatarColorHex: "#6750A4",
    pushToTalkPreferred: true,
    darkThemePreferred: true,
    blockedUserIds: [],
    createdAt: pastDate(10000),
    updatedAt: pastDate(10000),
    ...extra,
  });

  // Seed Alice (FREE initially, age 13+ verified), Bob, Charlie
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(makeUser(ALICE_UID, "Alice", "alice45", {
      proMember: false,
      proTier: "FREE",
      proExpiresAtMillis: 0,
      age13Verified: true,
      birthYear: 1995,
      totalSpentCents: 0,
    }));
    await db.collection("users").doc(BOB_UID).set(makeUser(BOB_UID, "Bob", "bob45"));
    await db.collection("users").doc(charlieUid).set(makeUser(charlieUid, "Charlie", "charlie45"));
  });

  // 1) Free user Group DM capped at 7 users (creating with 8 fails; creating with 7 succeeds; adding 8th fails)
  const freeGroupId = "group_tier_free_45";
  const baseThread = (ids) => ({
    participantIds: ids,
    isGroup: true,
    groupName: "Tiered Squad",
    groupAdminId: ALICE_UID,
    groupSupportIds: [],
    pttAllowedUserIds: [],
    userAId: ALICE_UID,
    userAName: "Alice",
    userAColorHex: "#6750A4",
    userATier: "USER",
    userBId: BOB_UID,
    userBName: "Bob",
    userBColorHex: "#0061A4",
    userBTier: "USER",
    lastMessageText: "Group created",
    lastMessageSenderId: ALICE_UID,
    unreadByUserIds: [BOB_UID],
    createdAt: pastDate(9000),
    updatedAt: pastDate(9000),
  });

  await assertFails(aliceDb.collection("directThreads").doc("group_free_8").set(baseThread(makeParticipants(8))));
  await assertSucceeds(aliceDb.collection("directThreads").doc(freeGroupId).set(baseThread(makeParticipants(7))));
  await assertFails(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      participantIds: makeParticipants(8),
      updatedAt: pastDate(8900),
    })
  );

  // 2) Group DM Support Staff permissions
  // Alice (Group Admin) promotes Bob to Group Support Staff
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      groupSupportIds: [BOB_UID],
      updatedAt: pastDate(8850),
    })
  );
  // Bob (Support Staff) CAN mute Charlie
  await assertSucceeds(
    bobDb.collection("directThreads").doc(freeGroupId).update({
      mutedUserIds: [charlieUid],
      updatedAt: pastDate(8800),
    })
  );
  // Bob (Support Staff) CAN bounce Charlie for 24h
  await assertSucceeds(
    bobDb.collection("directThreads").doc(freeGroupId).update({
      mutedUserIds: [],
      bouncedUntilByUserId: { [charlieUid]: Date.now() + 24 * 60 * 60 * 1000 },
      bouncedNamesByUserId: { [charlieUid]: "Charlie" },
      updatedAt: pastDate(8750),
    })
  );
  // Bob (Support Staff) CANNOT ban Charlie
  await assertFails(
    bobDb.collection("directThreads").doc(freeGroupId).update({
      bannedUserIds: [charlieUid],
      bannedNamesByUserId: { [charlieUid]: "Charlie" },
      updatedAt: pastDate(8700),
    })
  );
  // Bob (Support Staff) CANNOT mute Group Admin Alice
  await assertFails(
    bobDb.collection("directThreads").doc(freeGroupId).update({
      mutedUserIds: [ALICE_UID],
      updatedAt: pastDate(8650),
    })
  );
  // Bob (Support Staff) CANNOT promote Charlie to Group Support Staff
  await assertFails(
    bobDb.collection("directThreads").doc(freeGroupId).update({
      groupSupportIds: [BOB_UID, charlieUid],
      updatedAt: pastDate(8600),
    })
  );
  // Clear bounce on Charlie
  await assertSucceeds(
    bobDb.collection("directThreads").doc(freeGroupId).update({
      bouncedUntilByUserId: {},
      bouncedNamesByUserId: {},
      updatedAt: pastDate(8550),
    })
  );

  // 3) Upgrade Alice to VOX Pro ($9.99/mo -> 999 cents) => max 15 users (15 allowed, 16 denied)
  const exp30d = Date.now() + 29 * 24 * 60 * 60 * 1000;
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      proMember: true,
      proTier: "PRO",
      proExpiresAtMillis: exp30d,
      totalSpentCents: 999,
      updatedAt: pastDate(8500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      participantIds: makeParticipants(15),
      updatedAt: pastDate(8450),
    })
  );
  await assertFails(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      participantIds: makeParticipants(16),
      updatedAt: pastDate(8400),
    })
  );

  // 4) Upgrade Alice to VIP Pro ($29.99/mo -> +2999 cents) => max 25 users (16 & 25 allowed, 26 denied)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      proMember: true,
      proTier: "VIP_PRO",
      proExpiresAtMillis: exp30d,
      totalSpentCents: 999 + 2999,
      updatedAt: pastDate(8350),
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      participantIds: makeParticipants(25),
      updatedAt: pastDate(8300),
    })
  );
  await assertFails(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      participantIds: makeParticipants(26),
      updatedAt: pastDate(8250),
    })
  );

  // 5) >15-member PTT speaker control (currently 25 members!)
  // Charlie is NOT selected in pttAllowedUserIds -> DENIED turning on live PTT & DENIED sending voice clip
  await assertFails(
    charlieDb.collection("directThreads").doc(freeGroupId).update({
      pttEnabledUserIds: [charlieUid],
      updatedAt: pastDate(8200),
    })
  );
  await assertFails(
    charlieDb.collection("directThreads").doc(freeGroupId).collection("messages").doc("msg_ptt_denied").set({
      threadId: freeGroupId,
      participantIds: makeParticipants(25),
      senderId: charlieUid,
      senderName: "Charlie",
      senderTier: "USER",
      text: "",
      audioBase64: "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=",
      audioDurationSec: 3,
      createdAt: pastDate(8190),
    })
  );
  // Charlie CAN still send a regular text message
  await assertSucceeds(
    charlieDb.collection("directThreads").doc(freeGroupId).collection("messages").doc("msg_text_ok").set({
      threadId: freeGroupId,
      participantIds: makeParticipants(25),
      senderId: charlieUid,
      senderName: "Charlie",
      senderTier: "USER",
      text: "Text still works!",
      createdAt: pastDate(8180),
    })
  );
  // Group Support Staff Bob CANNOT modify pttAllowedUserIds
  await assertFails(
    bobDb.collection("directThreads").doc(freeGroupId).update({
      pttAllowedUserIds: [charlieUid],
      updatedAt: pastDate(8170),
    })
  );
  // Group Admin Alice selects Charlie for PTT
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      pttAllowedUserIds: [charlieUid],
      updatedAt: pastDate(8160),
    })
  );
  // Now Charlie CAN enable Live PTT and CAN send a PTT audio clip!
  await assertSucceeds(
    charlieDb.collection("directThreads").doc(freeGroupId).update({
      pttEnabledUserIds: [charlieUid],
      updatedAt: pastDate(8150),
    })
  );
  await assertSucceeds(
    charlieDb.collection("directThreads").doc(freeGroupId).collection("messages").doc("msg_ptt_ok").set({
      threadId: freeGroupId,
      participantIds: makeParticipants(25),
      senderId: charlieUid,
      senderName: "Charlie",
      senderTier: "USER",
      text: "",
      audioBase64: "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=",
      audioDurationSec: 3,
      createdAt: pastDate(8140),
    })
  );
  // Group Admin Alice revokes Charlie's PTT & removes from pttEnabledUserIds
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      pttAllowedUserIds: [],
      pttEnabledUserIds: [],
      updatedAt: pastDate(8130),
    })
  );
  await assertFails(
    charlieDb.collection("directThreads").doc(freeGroupId).update({
      pttEnabledUserIds: [charlieUid],
      updatedAt: pastDate(8120),
    })
  );

  // 6) Upgrade Alice to Elite Pro ($59.99/mo -> +5999 cents) => max 50 users (50 allowed, 51 denied)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      proMember: true,
      proTier: "ELITE_PRO",
      proExpiresAtMillis: exp30d,
      totalSpentCents: 999 + 2999 + 5999,
      updatedAt: pastDate(8100),
    })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      participantIds: makeParticipants(50),
      updatedAt: pastDate(8050),
    })
  );
  await assertFails(
    aliceDb.collection("directThreads").doc(freeGroupId).update({
      participantIds: makeParticipants(51),
      updatedAt: pastDate(8000),
    })
  );
});

test("46. Private room tier caps (25/50/75/100), Group DM Admin talk-over, and Admin custom identity fields", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const exp30d = Date.now() + 30 * 24 * 60 * 60 * 1000;

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set(validProfilePayload(ADMIN_UID, { displayName: "Owner Admin" }));
    await db.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, { displayName: "Alice Host", proMember: false, proTier: "FREE" }));
    await db.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, { displayName: "Bob Guest" }));
  });

  // 1) Private chat room user limits by host membership tier:
  // Free = 25, Pro = 50, VIP Pro = 75, Elite Pro = 100
  const privRoomId = "priv_room_cap_test";
  const makePrivRoom = async (participantCount) => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection("rooms").doc(privRoomId).set(
        validRoomPayload(ALICE_UID, { visibility: "PRIVATE", participantCount })
      );
      await ctx.firestore().collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).delete();
    });
  };

  // Free host: 24 -> Bob allowed; 25 -> Bob denied
  await makePrivRoom(24);
  await assertSucceeds(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));
  await makePrivRoom(25);
  await assertFails(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));

  // Pro host: 49 -> Bob allowed; 50 -> Bob denied
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).update({ proMember: true, proTier: "PRO", proExpiresAtMillis: exp30d });
  });
  await makePrivRoom(49);
  await assertSucceeds(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));
  await makePrivRoom(50);
  await assertFails(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));

  // VIP Pro host: 74 -> Bob allowed; 75 -> Bob denied
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).update({ proMember: true, proTier: "VIP_PRO", proExpiresAtMillis: exp30d });
  });
  await makePrivRoom(74);
  await assertSucceeds(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));
  await makePrivRoom(75);
  await assertFails(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));

  // Elite Pro host: 99 -> Bob allowed; 100 -> Bob denied
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).update({ proMember: true, proTier: "ELITE_PRO", proExpiresAtMillis: exp30d });
  });
  await makePrivRoom(99);
  await assertSucceeds(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));
  await makePrivRoom(100);
  await assertFails(bobDb.collection("rooms").doc(privRoomId).collection("participants").doc(BOB_UID).set(validParticipantPayload(BOB_UID, privRoomId)));

  // 2) Group DM Admin priority talk-over (`groupAdminTalking`)
  const grpId = "grp_talkover_46";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const ts = pastDate(9000);
    await ctx.firestore().collection("directThreads").doc(grpId).set({
      participantIds: [ALICE_UID, BOB_UID, "charlie_46"],
      isGroup: true,
      groupName: "Talk Over Squad",
      groupAdminId: ALICE_UID,
      groupAdminTalking: false,
      userAId: ALICE_UID,
      userAName: "Alice",
      userAColorHex: "#6C63FF",
      userATier: "USER",
      userBId: BOB_UID,
      userBName: "Bob",
      userBColorHex: "#00C4B4",
      userBTier: "USER",
      lastMessageText: "Ready",
      lastMessageSenderId: ALICE_UID,
      unreadByUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    });
  });

  // Group DM Admin Alice can toggle groupAdminTalking on/off
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(grpId).update({ groupAdminTalking: true, updatedAt: pastDate(5000) })
  );
  await assertSucceeds(
    aliceDb.collection("directThreads").doc(grpId).update({ groupAdminTalking: false, updatedAt: pastDate(4000) })
  );
  // Non-admin member Bob CANNOT toggle groupAdminTalking
  await assertFails(
    bobDb.collection("directThreads").doc(grpId).update({ groupAdminTalking: true, updatedAt: pastDate(3000) })
  );

  // 3) Admin custom identity disguise fields (`customDisplayName`, `customNameColorHex`, `customMembershipTier`)
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).update({
      customDisplayName: "Mystery VIP",
      customNameColorHex: "#FF00AA",
      customMembershipTier: "VIP_PRO",
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    adminDb.collection("userDirectory").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Mystery VIP",
      handle: "owner_admin",
      avatarColorHex: "#6C63FF",
      staffTier: "ADMIN",
      customDisplayName: "Mystery VIP",
      customNameColorHex: "#FF00AA",
      customMembershipTier: "VIP_PRO",
      updatedAt: pastDate(1500),
    })
  );
  // Regular user Bob CANNOT set custom identity disguise fields on users or userDirectory
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      customDisplayName: "Fake Admin",
      customNameColorHex: "#FF0000",
      customMembershipTier: "ADMIN",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("userDirectory").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      handle: "bob_guest",
      avatarColorHex: "#00C4B4",
      staffTier: "USER",
      customDisplayName: "Fake Admin",
      customNameColorHex: "#FF0000",
      customMembershipTier: "ADMIN",
      updatedAt: pastDate(500),
    })
  );
});

// 47. DEVICE BANS, MESSAGING DISABLEMENT BY STAFF/ADMIN, ADD-PEOPLE & AUTOMOD SPAM ESCALATION
test("Device bans, Staff/Admin disableMessaging enforcement, and automated spam self-suspension/reports work securely", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: "daj18801@gmail.com", email_verified: true })
    .firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: "support@vox.test", email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const deviceHash = "dev_aabbccddeeff00112233445566778899";
  const ts = pastDate(5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: "support@vox.test",
      displayName: "SupportSam",
      staffTier: "SUPPORT",
      canEnterInvisible: true,
      addedByUid: ADMIN_UID,
      addedByName: "OwnerAdmin",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      displayName: "Alice",
      lastDeviceId: deviceHash,
    });
    await db.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_vox",
      avatarColorHex: "#0088FF",
      staffTier: "USER",
      updatedAt: ts,
    });
    await db.collection("rooms").doc("room_msg_test").set({
      hostId: BOB_UID,
      hostName: "Bob",
      hostTier: "USER",
      title: "Chat Test Room",
      topic: "Testing messaging controls",
      category: "LOUNGE",
      coverKey: "HERO",
      visibility: "PUBLIC",
      status: "LIVE",
      videoEnabled: true,
      autoModEnabled: true,
      autoModBlockMode: false,
      linksBlocked: false,
      roomLocked: false,
      stageMicLocked: false,
      slowModeSeconds: 0,
      maxSpeakers: 6,
      participantCount: 1,
      moderatorIds: [BOB_UID],
      bannedUserIds: [],
      mutedUserIds: [],
      blockedWords: [],
      createdAt: ts,
      updatedAt: ts,
    });
  });

  // 1) Non-banned regular user CANNOT ban a device
  await assertFails(
    aliceDb.collection("bannedDevices").doc(deviceHash).set({
      deviceHash,
      bannedUserId: ALICE_UID,
      bannedUserName: "Alice",
      reason: "Fake ban",
      bannedByUid: ALICE_UID,
      bannedByName: "Alice",
      active: true,
      createdAt: pastDate(4000),
      updatedAt: pastDate(4000),
    })
  );

  // 2) Admin bans Alice's device -> another account (Bob) on the same device can GET bannedDevices/{deviceHash} to verify block, but CANNOT list all banned devices
  await assertSucceeds(
    adminDb.collection("bannedDevices").doc(deviceHash).set({
      deviceHash,
      bannedUserId: ALICE_UID,
      bannedUserName: "Alice",
      reason: "Banned account device lock",
      bannedByUid: ADMIN_UID,
      bannedByName: "OwnerAdmin",
      active: true,
      createdAt: pastDate(3500),
      updatedAt: pastDate(3500),
    })
  );
  await assertSucceeds(bobDb.collection("bannedDevices").doc(deviceHash).get());
  await assertFails(bobDb.collection("bannedDevices").get());

  // 3) Support Staff CAN disable and re-enable Alice's messaging (and mirror to userDirectory)
  await assertSucceeds(
    supportDb.collection("users").doc(ALICE_UID).update({
      disableMessaging: true,
      updatedAt: pastDate(3000),
    })
  );
  await assertSucceeds(
    supportDb.collection("userDirectory").doc(ALICE_UID).update({
      disableMessaging: true,
      updatedAt: pastDate(2900),
    })
  );

  // Alice CANNOT re-enable her own messaging
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      disableMessaging: false,
      updatedAt: pastDate(2800),
    })
  );

  // While disableMessaging == true, Alice CANNOT send a room message
  await assertFails(
    aliceDb.collection("rooms").doc("room_msg_test").collection("messages").doc("msg_blocked").set({
      roomId: "room_msg_test",
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "LISTENER",
      senderTier: "USER",
      text: "Trying to bypass disabled messaging",
      messageType: "TEXT",
      flaggedByAutoMod: false,
      deletedByMod: false,
      createdAt: pastDate(2700),
      updatedAt: pastDate(2700),
    })
  );

  // Support Staff re-enables Alice's messaging -> Alice can send messages again
  await assertSucceeds(
    supportDb.collection("users").doc(ALICE_UID).update({
      disableMessaging: false,
      updatedAt: pastDate(2500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_msg_test").collection("messages").doc("msg_ok").set({
      roomId: "room_msg_test",
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "LISTENER",
      senderTier: "USER",
      text: "Hello after re-enable",
      messageType: "TEXT",
      flaggedByAutoMod: false,
      deletedByMod: false,
      createdAt: pastDate(2400),
      updatedAt: pastDate(2400),
    })
  );

  // 4) Automated spam self-escalation (forward suspension only) + automated SPAM_SCAM staff report
  const nowMs = Date.now();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      spamViolationTimestamps: [nowMs - 60000, nowMs],
      suspendedUntilMillis: nowMs + 3600000,
      suspensionReason: "2nd message spam violation (>20 msgs in 30s): 1-hour suspension",
      updatedAt: pastDate(2000),
    })
  );
  // Alice CANNOT lower her own suspendedUntilMillis
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      spamViolationTimestamps: [nowMs - 60000, nowMs, nowMs + 1000],
      suspendedUntilMillis: 0,
      suspensionReason: "",
      updatedAt: pastDate(1500),
    })
  );
  // Automated spam report for self succeeds without hitting manual reportRateLimits
  await assertSucceeds(
    aliceDb.collection("safetyReports").doc("automod_spam_alice_3").set({
      reporterId: ALICE_UID,
      reporterName: "AutoMod",
      roomId: "AUTOMOD_SPAM",
      roomTitle: "Message Spam Auto-Mod",
      roomHostId: "SYSTEM_AUTOMOD",
      targetUserId: ALICE_UID,
      targetName: "Alice",
      category: "SPAM_SCAM",
      details: "3rd message spam violation (>20 messages in 30s). 4-hour suspension applied.",
      status: "OPEN",
      resolutionNote: "",
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  // Alice CANNOT abuse AUTOMOD_SPAM to file a report targeting Bob
  await assertFails(
    aliceDb.collection("safetyReports").doc("automod_spam_fake_bob").set({
      reporterId: ALICE_UID,
      reporterName: "AutoMod",
      roomId: "AUTOMOD_SPAM",
      roomTitle: "Message Spam Auto-Mod",
      roomHostId: "SYSTEM_AUTOMOD",
      targetUserId: BOB_UID,
      targetName: "Bob",
      category: "SPAM_SCAM",
      details: "Fake report against Bob",
      status: "OPEN",
      resolutionNote: "",
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("48. VIP Pro company sub-accounts, supervisors, work orders, employee requests, DND, and room/DM isolation", async () => {
  const OWNER_UID = "vip_owner_co";
  const EMP1_UID = "emp_sub_01";
  const SUP_UID = "emp_sup_02";
  const FREE_UID = "free_user_co";
  const OUTSIDER_UID = "outsider_co";
  const CO_THREAD_ID = `company_${OWNER_UID}`;
  const EMP1_CODE = "EMP_101AAA";
  const SUP_CODE = "EMP_202BBB";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(OWNER_UID).set(validProfilePayload(OWNER_UID, {
      displayName: "Acme Dispatch",
      proMember: true,
      proTier: "VIP_PRO",
      proExpiresAtMillis: Date.now() + 86400000 * 30,
    }));
    await db.collection("users").doc(FREE_UID).set(validProfilePayload(FREE_UID, { displayName: "FreeGuy" }));
    await db.collection("users").doc(EMP1_UID).set(validProfilePayload(EMP1_UID, { displayName: "EmpRaw" }));
    await db.collection("users").doc(SUP_UID).set(validProfilePayload(SUP_UID, { displayName: "SupRaw" }));
    await db.collection("users").doc(OUTSIDER_UID).set(validProfilePayload(OUTSIDER_UID, { displayName: "Outsider" }));
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const freeDb = testEnv.authenticatedContext(FREE_UID).firestore();
  const emp1Db = testEnv.authenticatedContext(EMP1_UID).firestore();
  const supDb = testEnv.authenticatedContext(SUP_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // 1. Free user CANNOT create company sub-account or company group DM
  await assertFails(
    freeDb.collection("companySubAccounts").doc("EMP_FREE01").set({
      ownerId: FREE_UID,
      ownerName: "FreeGuy",
      companyName: "Free Co",
      companyThreadId: `company_${FREE_UID}`,
      subName: "Worker 1",
      roleTitle: "Tech",
      accessCode: "EMP_FREE01",
      claimedUserId: "",
      claimedEmail: "",
      supervisor: false,
      supervisorUids: [],
      doNotDisturb: false,
      active: true,
      claimedAtMillis: 0,
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // 2. VIP Pro owner creates Company Private Group DM (starts with just owner)
  await assertSucceeds(
    ownerDb.collection("directThreads").doc(CO_THREAD_ID).set({
      participantIds: [OWNER_UID],
      isGroup: true,
      groupName: "Acme Dispatch HQ",
      groupAdminId: OWNER_UID,
      companyOwnerId: OWNER_UID,
      groupSupervisorIds: [],
      groupSupportIds: [],
      participantNamesById: { [OWNER_UID]: "Acme Dispatch" },
      userAId: OWNER_UID,
      userAName: "Acme Dispatch",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: OWNER_UID,
      userBName: "Acme Dispatch",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Company workspace ready",
      lastMessageSenderId: OWNER_UID,
      unreadByUserIds: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // 3. VIP Pro owner creates two sub-names
  await assertSucceeds(
    ownerDb.collection("companySubAccounts").doc(EMP1_CODE).set({
      ownerId: OWNER_UID,
      ownerName: "Acme Dispatch",
      companyName: "Acme Dispatch HQ",
      companyThreadId: CO_THREAD_ID,
      subName: "Truck 12 - Mike",
      roleTitle: "Driver",
      accessCode: EMP1_CODE,
      claimedUserId: "",
      claimedEmail: "",
      supervisor: false,
      supervisorUids: [],
      doNotDisturb: false,
      active: true,
      claimedAtMillis: 0,
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    ownerDb.collection("companySubAccounts").doc(SUP_CODE).set({
      ownerId: OWNER_UID,
      ownerName: "Acme Dispatch",
      companyName: "Acme Dispatch HQ",
      companyThreadId: CO_THREAD_ID,
      subName: "Lead - Sarah",
      roleTitle: "Shift Supervisor",
      accessCode: SUP_CODE,
      claimedUserId: "",
      claimedEmail: "",
      supervisor: false,
      supervisorUids: [],
      doNotDisturb: false,
      active: true,
      claimedAtMillis: 0,
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // 4. Employee 1 claims EMP1_CODE, binds profile as companySubAccount, and self-joins company thread
  await assertSucceeds(emp1Db.collection("companySubAccounts").doc(EMP1_CODE).get());
  await assertSucceeds(
    emp1Db.collection("companySubAccounts").doc(EMP1_CODE).update({
      claimedUserId: EMP1_UID,
      claimedEmail: "mike@acme.test",
      claimedAtMillis: Date.now(),
      updatedAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    emp1Db.collection("users").doc(EMP1_UID).update({
      companySubAccount: true,
      parentCompanyOwnerId: OWNER_UID,
      companyName: "Acme Dispatch HQ",
      companyThreadId: CO_THREAD_ID,
      subAccountId: EMP1_CODE,
      displayName: "Truck 12 - Mike",
      doNotDisturb: false,
      updatedAt: pastDate(1400),
    })
  );
  await assertSucceeds(
    emp1Db.collection("directThreads").doc(CO_THREAD_ID).update({
      participantIds: [OWNER_UID, EMP1_UID],
      participantNamesById: { [OWNER_UID]: "Acme Dispatch", [EMP1_UID]: "Truck 12 - Mike" },
      updatedAt: pastDate(1300),
    })
  );

  // Supervisor claims SUP_CODE & self-joins
  await assertSucceeds(
    supDb.collection("companySubAccounts").doc(SUP_CODE).update({
      claimedUserId: SUP_UID,
      claimedEmail: "sarah@acme.test",
      claimedAtMillis: Date.now(),
      updatedAt: pastDate(1250),
    })
  );
  await assertSucceeds(
    supDb.collection("users").doc(SUP_UID).update({
      companySubAccount: true,
      parentCompanyOwnerId: OWNER_UID,
      companyName: "Acme Dispatch HQ",
      companyThreadId: CO_THREAD_ID,
      subAccountId: SUP_CODE,
      displayName: "Lead - Sarah",
      doNotDisturb: false,
      updatedAt: pastDate(1200),
    })
  );
  await assertSucceeds(
    supDb.collection("directThreads").doc(CO_THREAD_ID).update({
      participantIds: [OWNER_UID, EMP1_UID, SUP_UID],
      participantNamesById: {
        [OWNER_UID]: "Acme Dispatch",
        [EMP1_UID]: "Truck 12 - Mike",
        [SUP_UID]: "Lead - Sarah",
      },
      updatedAt: pastDate(1150),
    })
  );

  // 5. Sub-account CANNOT create rooms, create other DMs, or send external friend requests
  await assertFails(
    emp1Db.collection("rooms").doc("sub_room_blocked").set(validRoomPayload(EMP1_UID, { title: "Blocked Room" }))
  );
  await assertFails(
    emp1Db.collection("friendships").doc(`${EMP1_UID}_${OUTSIDER_UID}`).set({
      userIds: [EMP1_UID, OUTSIDER_UID],
      requesterId: EMP1_UID,
      requesterName: "Truck 12 - Mike",
      requesterHandle: "mike12",
      requesterColorHex: "#0088FF",
      requesterTier: "USER",
      recipientId: OUTSIDER_UID,
      recipientName: "Outsider",
      recipientHandle: "outsider",
      recipientColorHex: "#0088FF",
      recipientTier: "USER",
      status: "PENDING",
      createdAt: pastDate(1100),
      updatedAt: pastDate(1100),
    })
  );
  await assertFails(
    emp1Db.collection("directThreads").doc(`${EMP1_UID}_${OUTSIDER_UID}`).set({
      participantIds: [EMP1_UID, OUTSIDER_UID],
      userAId: EMP1_UID,
      userAName: "Truck 12 - Mike",
      userAColorHex: "#0088FF",
      userATier: "USER",
      userBId: OUTSIDER_UID,
      userBName: "Outsider",
      userBColorHex: "#0088FF",
      userBTier: "USER",
      lastMessageText: "Hi",
      lastMessageSenderId: EMP1_UID,
      unreadByUserIds: [OUTSIDER_UID],
      createdAt: pastDate(1100),
      updatedAt: pastDate(1100),
    })
  );

  // Sub-account CAN toggle Do Not Disturb & update displayName
  await assertSucceeds(
    emp1Db.collection("users").doc(EMP1_UID).update({
      doNotDisturb: true,
      displayName: "Truck 12 - Mike B",
      updatedAt: pastDate(1050),
    })
  );
  await assertSucceeds(
    emp1Db.collection("companySubAccounts").doc(EMP1_CODE).update({
      doNotDisturb: true,
      subName: "Truck 12 - Mike B",
      updatedAt: pastDate(1040),
    })
  );

  // 6. Owner appoints SUP_UID as Group DM Supervisor
  await assertSucceeds(
    ownerDb.collection("directThreads").doc(CO_THREAD_ID).update({
      groupSupervisorIds: [SUP_UID],
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    ownerDb.collection("companySubAccounts").doc(SUP_CODE).update({
      supervisor: true,
      supervisorUids: [SUP_UID],
      updatedAt: pastDate(990),
    })
  );

  // 7. Supervisor sends an URGENT Work Order to Employee 1; Outsider CANNOT
  await assertSucceeds(
    supDb.collection("workOrders").doc("wo_urgent_01").set({
      companyOwnerId: OWNER_UID,
      threadId: CO_THREAD_ID,
      supervisorUids: [SUP_UID],
      senderId: SUP_UID,
      senderName: "Lead - Sarah",
      senderRole: "Supervisor",
      targetUserId: EMP1_UID,
      targetName: "Truck 12 - Mike B",
      orderType: "WORK_ORDER",
      priority: "URGENT",
      title: "Dock 4 Priority Pickup",
      body: "Head to Dock 4 immediately for priority pallet load.",
      status: "OPEN",
      acknowledged: false,
      acknowledgedAtMillis: 0,
      completedAtMillis: 0,
      createdAt: pastDate(900),
      updatedAt: pastDate(900),
    })
  );
  await assertFails(
    outsiderDb.collection("workOrders").doc("wo_fake_outsider").set({
      companyOwnerId: OWNER_UID,
      threadId: CO_THREAD_ID,
      supervisorUids: [SUP_UID],
      senderId: OUTSIDER_UID,
      senderName: "Outsider",
      senderRole: "Fake",
      targetUserId: EMP1_UID,
      targetName: "Truck 12 - Mike B",
      orderType: "NOTE",
      priority: "NORMAL",
      title: "Spoof",
      body: "Unauthorized note",
      status: "OPEN",
      acknowledged: false,
      acknowledgedAtMillis: 0,
      completedAtMillis: 0,
      createdAt: pastDate(850),
      updatedAt: pastDate(850),
    })
  );

  // Employee 1 acknowledges & completes Work Order; Outsider cannot read it
  await assertFails(outsiderDb.collection("workOrders").doc("wo_urgent_01").get());
  await assertSucceeds(
    emp1Db.collection("workOrders").doc("wo_urgent_01").update({
      status: "COMPLETED",
      acknowledged: true,
      acknowledgedAtMillis: Date.now(),
      completedAtMillis: Date.now(),
      updatedAt: pastDate(800),
    })
  );

  // 8. Employee 1 submits an Employee Request; Supervisor approves it
  await assertSucceeds(
    emp1Db.collection("employeeRequests").doc("req_01").set({
      companyOwnerId: OWNER_UID,
      threadId: CO_THREAD_ID,
      supervisorUids: [SUP_UID],
      requesterId: EMP1_UID,
      requesterName: "Truck 12 - Mike B",
      category: "SUPPLIES",
      subject: "Need replacement straps",
      details: "Two ratchet straps snapped on route 9.",
      urgent: false,
      status: "PENDING",
      reviewedById: "",
      reviewedByName: "",
      reviewerNote: "",
      createdAt: pastDate(700),
      updatedAt: pastDate(700),
    })
  );
  await assertSucceeds(
    supDb.collection("employeeRequests").doc("req_01").update({
      status: "APPROVED",
      reviewedById: SUP_UID,
      reviewedByName: "Lead - Sarah",
      reviewerNote: "Pick up 4 straps from Bay 2.",
      updatedAt: pastDate(600),
    })
  );
});















