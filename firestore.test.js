const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

// 1. Unauthenticated Security Bounds
test("Unauthenticated: cannot read habits", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("habits").get());
});

test("Unauthenticated: cannot create habit", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("habits").doc("h1").set({
      userId: ALICE_UID,
      title: "Reading",
      category: "Study",
      createdAt: new Date(),
    })
  );
});

// 2. Cross-User Isolation
test("Cross-user: Bob cannot read Alice's habits", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("habits").doc("h1").set({
      userId: ALICE_UID,
      title: "Deep Work",
      category: "Work",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("habits").doc("h1").get());
});

test("Cross-user: Bob cannot write to Alice's sessions", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("s1").set({
      userId: ALICE_UID,
      durationMinutes: 25,
      tag: "Study",
      sessionType: "pomodoro",
      completed: true,
      createdAt: new Date(),
    })
  );
});

// 3. Authenticated Owner Access
test("Authenticated owner: can create and read own habit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("h1").set({
      userId: ALICE_UID,
      title: "Coding",
      category: "Work",
      targetDaysPerWeek: 5,
      currentStreak: 0,
      bestStreak: 0,
      completedDates: [],
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").get()
  );
});

test("Authenticated owner: can create and read own focus session", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("s1").set({
      userId: ALICE_UID,
      durationMinutes: 25,
      tag: "Coding",
      sessionType: "pomodoro",
      completed: true,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("s1").get()
  );
});
