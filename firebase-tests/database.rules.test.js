import fs from "node:fs";
import path from "node:path";
import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { get, ref, set, update, remove } from "firebase/database";

const projectId = "demo-menu-kiosk";
const rules = fs.readFileSync(
  path.resolve("../../AI-Operations-Management-Platform-main/database.rules.json"),
  "utf8"
);
const enabledUid = process.env.KIOSK_UID_1 ?? "REPLACE_WITH_KIOSK_UID_1";
const otherEnabledUid = process.env.KIOSK_UID_2 ?? "REPLACE_WITH_KIOSK_UID_2";
const disabledUid = "disabled-kiosk";
const companyId = "company-sugar-cafe";
const branchId = "branch-sugar-cafe-nivel-hills";
const otherBranchId = "branch-sugar-cafe-it-park";
const managerUid = "company-manager";
const companyKioskUid = "company-kiosk";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({ projectId, database: { rules } });
});

beforeEach(async () => {
  await testEnv.clearDatabase();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.database();
    await set(ref(db), {
      branch2: {
        categories: { Drinks: { coffee: { name: "Coffee", price: 100 } } },
        appSettings: { backgroundTheme: "Dark" },
        inventory: {
          Drinks: { coffee: { sizes: { Medium: { stock: 5 } } } },
        },
      },
      [companyId]: {
        companyProfile: {
          companyId,
          companyName: "Sugar Cafe Group",
          ownerUids: { [managerUid]: true },
        },
        users: {
          [managerUid]: {
            uid: managerUid,
            email: "manager@example.com",
            companyId,
            companyRole: "owner",
            branchIds: { [branchId]: true },
          },
        },
        branches: {
          [branchId]: {
            branchProfile: {
              branchId,
              branchName: "Nivel Hills",
              companyId,
              ownerUid: managerUid,
              plan: "free",
            },
            users: {
              [managerUid]: { uid: managerUid, role: "owner" },
            },
            kiosks: {},
            categories: { Drinks: { coffee: { name: "Coffee", price: 100 } } },
            inventory: { Drinks: { coffee: { sizes: { Medium: { stock: 5 } } } } },
            logs: {},
          },
          [otherBranchId]: {
            branchProfile: {
              branchId: otherBranchId,
              branchName: "IT Park",
              companyId,
              ownerUid: managerUid,
              plan: "free",
            },
            users: {},
          },
        },
      },
    });
  });
});

after(async () => testEnv.cleanup());

function kiosk(uid) {
  return testEnv.authenticatedContext(uid, {
    firebase: { sign_in_provider: "anonymous" },
  }).database();
}

function validOrder(uid = enabledUid, id = "123e4567-e89b-12d3-a456-426614174000") {
  return {
    orderId: id,
    submittedByUid: uid,
    orderNumber: "123E4567",
    customerName: "Guest",
    items: [{ name: "Coffee", size: "Medium", quantity: 1, price: 100, subtotal: 100 }],
    total: 100,
    paymentMethod: "COUNTER",
    paymentStatus: "PAY_AT_COUNTER",
    timestamp: { ".sv": "timestamp" },
    inventoryProcessed: true,
    inventoryProcessedAt: { ".sv": "timestamp" },
    orderSource: "android_kiosk",
  };
}

test("company users can read only their company branches", async () => {
  const manager = testEnv.authenticatedContext(managerUid).database();
  const otherCompany = testEnv.authenticatedContext("other-company-user").database();

  await assertSucceeds(get(ref(manager, `${companyId}/branches/${branchId}/categories`)));
  await assertFails(get(ref(otherCompany, `${companyId}/branches/${branchId}/categories`)));
  await assertFails(get(ref(manager, "company-other/branches/other-branch/categories")));
});

test("branch owner can enroll a kiosk and only that kiosk can read enrollment", async () => {
  const manager = testEnv.authenticatedContext(managerUid).database();
  const kioskDb = kiosk(companyKioskUid);
  const otherKioskDb = kiosk("company-other-kiosk");
  const enrollment = {
    kioskUid: companyKioskUid,
    companyId,
    branchId,
    isActive: true,
  };

  // Full enrollment record lives under the company node.
  await assertSucceeds(set(ref(manager, `${companyId}/kioskEnrollments/${companyKioskUid}`), {
    ...enrollment,
    name: "Nivel Hills Kiosk",
    registeredAt: { ".sv": "timestamp" },
    lastActiveAt: { ".sv": "timestamp" },
    updatedAt: { ".sv": "timestamp" },
  }));
  await assertSucceeds(get(ref(kioskDb, `${companyId}/kioskEnrollments/${companyKioskUid}`)));
  await assertFails(get(ref(otherKioskDb, `${companyId}/kioskEnrollments/${companyKioskUid}`)));

  // Root index is a minimal pointer (companyId, branchId, isActive — no kioskUid/name).
  await assertSucceeds(set(ref(manager, `kioskEnrollments/${companyKioskUid}`), {
    companyId,
    branchId,
    isActive: true,
    updatedAt: { ".sv": "timestamp" },
  }));
  await assertSucceeds(get(ref(kioskDb, `kioskEnrollments/${companyKioskUid}`)));
  await assertFails(get(ref(otherKioskDb, `kioskEnrollments/${companyKioskUid}`)));
});

test("company owner can delete a branch; non-owner cannot", async () => {
  const manager = testEnv.authenticatedContext(managerUid).database();
  const stranger = testEnv.authenticatedContext("stranger").database();

  // Owner (in companyProfile.ownerUids) may remove the whole branch node.
  await assertSucceeds(remove(ref(manager, `${companyId}/branches/${otherBranchId}`)));
  // A stranger cannot remove a branch.
  await assertFails(remove(ref(stranger, `${companyId}/branches/${branchId}`)));
});

test("an enrolled kiosk can write only its assigned branch order and stock update", async () => {
  const manager = testEnv.authenticatedContext(managerUid).database();
  const kioskDb = kiosk(companyKioskUid);
  await assertSucceeds(set(ref(manager, `${companyId}/branches/${branchId}/kiosks/${companyKioskUid}`), {
    kioskUid: companyKioskUid,
    name: "Nivel Hills Kiosk",
    isActive: true,
  }));

  const id = "123e4567-e89b-12d3-a456-426614174001";
  const order = validOrder(companyKioskUid, id);
  await assertSucceeds(update(ref(kioskDb), {
    [`${companyId}/branches/${branchId}/logs/${id}`]: order,
    [`${companyId}/branches/${branchId}/inventory/Drinks/coffee/sizes/Medium/stock`]: 4,
  }));
  await assertSucceeds(get(ref(kioskDb, `${companyId}/branches/${branchId}/appSettings`)));
  await assertFails(get(ref(kioskDb, `${companyId}/branches/${otherBranchId}/categories`)));
});

test("two-step workspace onboarding mirrors the app writes", async () => {
  const owner = testEnv.authenticatedContext("new-owner").database();
  const co = "company-touch-co-test";
  const br = "branch-touch-co-test";

  // Step 1a: companyProfile via single-path set() (bootstrap ownership)
  await assertSucceeds(set(ref(owner, `${co}/companyProfile`), {
    companyId: co,
    companyName: "Touch Co",
    ownerUids: { "new-owner": true },
    createdAt: { ".sv": "timestamp" },
  }));
  // Step 1b: minimal branchProfile (validated fields)
  await assertSucceeds(set(ref(owner, `${co}/branches/${br}/branchProfile`), {
    branchId: br,
    branchName: "Main",
    companyId: co,
    ownerUid: "new-owner",
    plan: "free",
  }));
  // Step 1c: extended branchProfile fields, one at a time (app behavior)
  const extended = {
    businessName: "Touch Co",
    companyName: "Touch Co",
    name: "Main",
    location: "Cebu City",
    serviceType: "restaurant",
    contactPhone: "09321234567",
    currency: "PHP",
    timezone: "Asia/Manila",
    operatingHours: "9:00 AM - 9:00 PM",
    subscriptionStatus: "inactive",
    trialEndsAt: null,
    createdAt: { ".sv": "timestamp" },
  };
  for (const [f, v] of Object.entries(extended)) {
    await assertSucceeds(set(ref(owner, `${co}/branches/${br}/branchProfile/${f}`), v));
  }

  // Step 2: user profile + appSettings + branch users, after ownership exists
  await assertSucceeds(set(ref(owner, `${co}/users/new-owner`), {
    uid: "new-owner",
    email: "owner@example.com",
    companyId: co,
    companyRole: "owner",
    role: "owner",
    branchIds: { [br]: true },
  }));
  await assertSucceeds(set(ref(owner, `${co}/branches/${br}/appSettings`), {
    businessName: "Touch Co",
    branchLocation: "Cebu City",
    currency: "PHP",
    timezone: "Asia/Manila",
    operatingHours: "9:00 AM - 9:00 PM",
    backgroundTheme: "Default",
  }));
  await assertSucceeds(set(ref(owner, `${co}/branches/${br}/users`), {
    "new-owner": { uid: "new-owner", email: "owner@example.com", role: "owner" },
  }));

  // Step 3: the FULL workspace object must be persisted under users/$uid/workspace.
  // (Regression test for the `/home/null` grey-screen bug: a bare
  //  { onboardingComplete: true } stub leaves branchId undefined, so the app's
  //  getUserBranch() returns null and LoginPage navigates to `/home/null`.)
  await assertSucceeds(set(ref(owner, `${co}/users/new-owner/workspace`), {
    companyId: co,
    companyName: "Touch Co",
    branchId: br,
    businessName: "Touch Co",
    branchName: "Main",
    location: "Cebu City",
    serviceType: "restaurant",
    contactPhone: "09321234567",
    currency: "PHP",
    timezone: "Asia/Manila",
    operatingHours: "9:00 AM - 9:00 PM",
    plan: "free",
    subscriptionStatus: "inactive",
    onboardingComplete: true,
    updatedAt: { ".sv": "timestamp" },
    branches: {
      [br]: {
        branchId: br,
        name: "Main",
        location: "Cebu City",
        serviceType: "restaurant",
        contactPhone: "09321234567",
        currency: "PHP",
        timezone: "Asia/Manila",
        operatingHours: "9:00 AM - 9:00 PM",
        plan: "free",
        subscriptionStatus: "inactive",
      },
    },
  }));

  // Step 4: accounts/{uid} must include uid + companyId + activeBranchId.
  // (Regression for the add-branch permission_denied: writing only
  //  { activeBranchId, updatedAt } violates accounts .validate
  //  hasChildren(['uid','companyId','activeBranchId']) → denied.)
  await assertSucceeds(set(ref(owner, `accounts/new-owner`), {
    uid: "new-owner",
    companyId: co,
    activeBranchId: br,
    updatedAt: { ".sv": "timestamp" },
  }));
  // And the old incomplete form must FAIL validation.
  await assertFails(set(ref(owner, `accounts/new-owner`), {
    activeBranchId: br,
    updatedAt: { ".sv": "timestamp" },
  }));
});

test("retry after partial company works", async () => {
  const owner = testEnv.authenticatedContext("new-owner").database();
  const co = "company-partial-test";
  const br = "branch-partial-test";
  // Emulate a first attempt that only wrote companyProfile (e.g. network failure mid-write).
  await assertSucceeds(set(ref(owner, `${co}/companyProfile`), {
    companyId: co,
    companyName: "Partial Co",
    ownerUids: { "new-owner": true },
  }));
  // Retry Step 1b: company profile already exists, now add the branch.
  await assertSucceeds(set(ref(owner, `${co}/branches/${br}/branchProfile`), {
    branchId: br,
    branchName: "Main",
    companyId: co,
    ownerUid: "new-owner",
    plan: "free",
  }));
});
