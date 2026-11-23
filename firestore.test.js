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

// 1. UNAUTHENTICATED DENIAL
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauthDb.collection("staffRoles").get());
  await assertFails(unauthDb.collection("userAlerts").get());
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

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));

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

  // Bob unlocks Alice's locked photo: spends 2 pts, adds self to unlockedByUserIds, credits Alice $1 (100c), logs $1/$1 split!
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
      creatorEarningsCents: 100,
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
      creatorShareCents: 100,
      platformShareCents: 100,
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

  // Bob subscribes to Alice for $15/month (30 days)
  const subId = `${BOB_UID}_${ALICE_UID}`;
  await assertSucceeds(
    bobDb.collection("creatorSubscriptions").doc(subId).set({
      subscriberId: BOB_UID,
      creatorId: ALICE_UID,
      creatorName: "Alice",
      priceCents: 1500,
      creatorShareCents: 750,
      platformShareCents: 750,
      active: true,
      expiresAtMillis: Date.now() + 30 * 24 * 60 * 60 * 1000,
      createdAt: pastDate(1800),
      updatedAt: pastDate(1800),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).update({
      creatorEarningsCents: 850,
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
      creatorShareCents: 750,
      platformShareCents: 750,
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

  // 3) Bob pays $10 ($5 to Alice / $5 to app owner) -> creates dmAccessPasses/{bob}_{alice} + credits Alice 500c + UNLOCK_DM ledger
  const passId = `${BOB_UID}_${ALICE_UID}`;
  const payBatch = bobDb.batch();
  payBatch.set(bobDb.collection("dmAccessPasses").doc(passId), {
    senderId: BOB_UID,
    senderName: "Bob",
    recipientId: ALICE_UID,
    recipientName: "Alice",
    priceCents: 1000,
    creatorShareCents: 500,
    platformShareCents: 500,
    active: true,
    kicked: false,
    createdAt: pastDate(3500),
    updatedAt: pastDate(3500),
  });
  payBatch.update(bobDb.collection("users").doc(BOB_UID), {
    totalSpentCents: 1000,
    updatedAt: pastDate(3500),
  });
  payBatch.update(bobDb.collection("users").doc(ALICE_UID), {
    creatorEarningsCents: 500,
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
    grossCents: 1000,
    creatorShareCents: 500,
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






