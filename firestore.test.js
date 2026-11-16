const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTime = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ROOM_ID = "subject_8906247916759695608_s0_e0";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read rooms, messages, or watchlist", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("rooms").where("isLive", "==", true).get());
  await assertFails(unauthDb.collection("rooms").doc(ROOM_ID).collection("messages").where("roomId", "==", ROOM_ID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watchlist").get());
});

test("Authenticated user: can create/read own profile and watchlist, cannot access Bob's", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("8906247916759695608").set({
      userId: ALICE_UID,
      subjectId: "8906247916759695608",
      title: "Avatar",
      mediaType: "Movie",
      lastSeason: 0,
      lastEpisode: 0,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("watchlist").get()
  );

  // Ghost field shadow update test
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      isAdmin: true,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );
});

test("Watch rooms and real-time chat: authenticated users can create room, chat, and query with matching filters", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Unfiltered rooms query fails (rules are not filters)
  await assertFails(aliceDb.collection("rooms").get());

  // Filtered live rooms query succeeds even when empty
  await assertSucceeds(aliceDb.collection("rooms").where("isLive", "==", true).get());

  // Cannot post message before room exists (parent gate)
  await assertFails(
    aliceDb.collection("rooms").doc(ROOM_ID).collection("messages").doc("msg_1").set({
      roomId: ROOM_ID,
      userId: ALICE_UID,
      senderName: "Alice",
      text: "Hello!",
      messageType: "chat",
      createdAt: serverTime(),
    })
  );

  // Alice creates room
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_ID).set({
      roomId: ROOM_ID,
      subjectId: "8906247916759695608",
      title: "Avatar",
      mediaType: "Movie",
      season: 0,
      episode: 0,
      isLive: true,
      createdBy: ALICE_UID,
      hostName: "Alice",
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );

  // Bob posts chat message
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_ID).collection("messages").doc("msg_1").set({
      roomId: ROOM_ID,
      userId: BOB_UID,
      senderName: "Bob",
      text: "1080p stream is super crisp!",
      streamQuality: "1080p",
      messageType: "chat",
      createdAt: serverTime(),
    })
  );

  // Identity spoofing fails (Bob pretending to be Alice)
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_ID).collection("messages").doc("msg_spoof").set({
      roomId: ROOM_ID,
      userId: ALICE_UID,
      senderName: "Alice",
      text: "Spoofed!",
      messageType: "chat",
      createdAt: serverTime(),
    })
  );

  // Alice cannot delete Bob's message, Bob can delete his own
  await assertFails(aliceDb.collection("rooms").doc(ROOM_ID).collection("messages").doc("msg_1").delete());
  await assertSucceeds(bobDb.collection("rooms").doc(ROOM_ID).collection("messages").doc("msg_1").delete());

  // Filtered message query succeeds, unfiltered fails
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_ID).collection("messages").where("roomId", "==", ROOM_ID).get()
  );
  await assertFails(
    aliceDb.collection("rooms").doc(ROOM_ID).collection("messages").get()
  );

  // Presence heartbeat: Bob can write own presence, cannot overwrite Alice's
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_ID).collection("presence").doc(BOB_UID).set({
      roomId: ROOM_ID,
      userId: BOB_UID,
      displayName: "Bob",
      activeQuality: "1080p",
      lastSeenAt: serverTime(),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_ID).collection("presence").doc(ALICE_UID).set({
      roomId: ROOM_ID,
      userId: ALICE_UID,
      displayName: "Alice",
      lastSeenAt: serverTime(),
    })
  );
});
