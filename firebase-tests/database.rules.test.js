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
// Role-gating fixtures. `managerUid` above is the COMPANY owner (it sits in
// ownerUids). These two are the delegated roles underneath them.
const branchManagerUid = "branch-manager";
const staffUid = "branch-staff";
const outsiderUid = "outside-company-user";
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
          [branchManagerUid]: {
            uid: branchManagerUid,
            email: "bm@example.com",
            companyId,
            companyRole: "manager",
            branchIds: { [branchId]: true },
          },
          [staffUid]: {
            uid: staffUid,
            email: "staff@example.com",
            companyId,
            companyRole: "staff",
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
              // Structurally one manager per branch: the manager role is derived
              // from this pointer, not from the membership row alone.
              managerUid: branchManagerUid,
              plan: "free",
            },
            users: {
              [managerUid]: { uid: managerUid, role: "owner" },
              [branchManagerUid]: { uid: branchManagerUid, role: "manager" },
              [staffUid]: { uid: staffUid, role: "staff" },
            },
            kiosks: {},
            categories: { Drinks: { coffee: { name: "Coffee", price: 100 } } },
            inventory: { Drinks: { coffee: { sizes: { Medium: { stock: 5 } } } } },
            analytics: { summary: { totalOrders: 1, totalRevenue: 100 } },
            deletedLogs: { "123E4567": { orderNumber: "123E4567", total: 100 } },
            inventoryHistory: { coffee: { "-N1": { type: "increase", quantity: 1 } } },
            menuLogs: { "-N1": { email: "manager@example.com", action: "Added item", timestamp: 1 } },
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

// ===== Delegated roles: owner / branch manager / staff =====
//
// These fixtures mirror how the portal writes: staff restock and toggle
// availability, managers additionally edit the menu and correct the books, and
// the company owner keeps branch + billing control.

const branchPath = `${companyId}/branches/${branchId}`;

test("staff can append inventory history, which is written on every restock", async () => {
  const staff = testEnv.authenticatedContext(staffUid).database();
  const manager = testEnv.authenticatedContext(branchManagerUid).database();

  // adjustStock() updates stock and then appends a history entry. If this write
  // is denied the stock correction lands half-applied and throws to the caller.
  await assertSucceeds(set(ref(staff, `${branchPath}/inventoryHistory/coffee/-N2`), {
    type: "increase",
    quantity: 2,
    userId: staffUid,
  }));
  await assertSucceeds(set(ref(manager, `${branchPath}/inventoryHistory/coffee/-N3`), {
    type: "decrease",
    quantity: 1,
    userId: branchManagerUid,
  }));
  await assertSucceeds(get(ref(staff, `${branchPath}/inventoryHistory/coffee`)));
});

test("only managers and owners may write the menu audit trail", async () => {
  const staff = testEnv.authenticatedContext(staffUid).database();
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const owner = testEnv.authenticatedContext(managerUid).database();

  // addMenuLog() rides along with every menu edit, so it must succeed for the
  // two roles that can edit the menu and fail for the one that cannot.
  await assertSucceeds(set(ref(manager, `${branchPath}/menuLogs/-M1`), {
    email: "bm@example.com",
    action: "Added item",
    timestamp: 2,
  }));
  await assertSucceeds(set(ref(owner, `${branchPath}/menuLogs/-M2`), {
    email: "manager@example.com",
    action: "Edited item",
    timestamp: 3,
  }));
  await assertFails(set(ref(staff, `${branchPath}/menuLogs/-M3`), {
    email: "staff@example.com",
    action: "Added item",
    timestamp: 4,
  }));
});

test("staff can read the trash bin but not write it; only the owner can empty it", async () => {
  const staff = testEnv.authenticatedContext(staffUid).database();
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const owner = testEnv.authenticatedContext(managerUid).database();

  // Every member's BranchDataContext subscribes to deletedLogs, so reads stay open.
  await assertSucceeds(get(ref(staff, `${branchPath}/deletedLogs`)));

  // Discarding an order is a manager action; wiping the bin outright is not.
  await assertSucceeds(set(ref(manager, `${branchPath}/deletedLogs/ORDER-BM`), {
    orderNumber: "ORDER-BM",
    total: 50,
  }));
  await assertFails(set(ref(staff, `${branchPath}/deletedLogs/ORDER-ST`), {
    orderNumber: "ORDER-ST",
    total: 50,
  }));
  await assertSucceeds(remove(ref(owner, `${branchPath}/deletedLogs`)));
  await assertFails(remove(ref(manager, `${branchPath}/deletedLogs`)));
  await assertFails(remove(ref(staff, `${branchPath}/deletedLogs`)));
});

test("order processing can write analytics from any member's session", async () => {
  const staff = testEnv.authenticatedContext(staffUid).database();
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const outsider = testEnv.authenticatedContext(outsiderUid).database();

  // useAnalyticsProcessor() runs inside BranchDataContext, so whoever is signed
  // in processes new orders. Restricting this to managers would strand orders
  // placed on a staff-only shift, so every member may write.
  await assertSucceeds(set(ref(staff, `${branchPath}/analytics/processedOrders/o-1`), {
    orderId: "o-1",
    total: 10,
  }));
  await assertSucceeds(set(ref(manager, `${branchPath}/analytics/processedOrders/o-2`), {
    orderId: "o-2",
    total: 20,
  }));

  await assertFails(set(ref(outsider, `${branchPath}/analytics/processedOrders/o-3`), {
    orderId: "o-3",
    total: 30,
  }));
  await assertFails(get(ref(outsider, `${branchPath}/analytics`)));
});

test("the order ledger is closed to branch managers and staff", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const staff = testEnv.authenticatedContext(staffUid).database();
  const id = "123e4567-e89b-12d3-a456-426614174010";
  const logsPath = `${branchPath}/logs`;

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `${logsPath}/${id}`), validOrder(companyKioskUid, id));
  });

  // Running the branch does not include rewriting its takings. OrdersPage hides
  // the trash action for kiosk orders and HistoryPage sends them to a read-only
  // view; these assertions keep the rules behind those screens.
  await assertFails(update(ref(manager, `${logsPath}/${id}`), { total: 1 }));
  await assertFails(update(ref(staff, `${logsPath}/${id}`), { total: 1 }));
  await assertFails(remove(ref(manager, `${logsPath}/${id}`)));
  await assertFails(remove(ref(staff, `${logsPath}/${id}`)));

  // Reading it back is what the dashboards do, so that stays open.
  await assertSucceeds(get(ref(manager, `${logsPath}/${id}`)));
  await assertSucceeds(get(ref(staff, `${logsPath}/${id}`)));
});

test("an order's recorded fields cannot be rewritten in place, even by the owner", async () => {
  const owner = testEnv.authenticatedContext(managerUid).database();
  const id = "123e4567-e89b-12d3-a456-426614174011";
  const logsPath = `${branchPath}/logs`;

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `${logsPath}/${id}`), validOrder(companyKioskUid, id));
  });

  // Branch rules cascade downward, so the ownerUids grant on $branchId reaches
  // /logs and an owner can hard-delete an order outright. Without a Cloud
  // Function the tenant's own owner is trusted with their data, so that is
  // accepted rather than closed — but it is asserted here so the choice is
  // visible, and revisited deliberately if that grant is ever narrowed.
  await assertSucceeds(remove(ref(owner, `${logsPath}/${id}`)));

  // Rewriting is a different matter and stays shut: submittedByUid is pinned to
  // the kiosk that placed the order, and any key the shape does not declare is
  // rejected, so tampering with an order means deleting it, not editing it.
  await assertFails(update(ref(owner, `${logsPath}/${id}`), { total: 1 }));
  await assertFails(update(ref(owner, `${logsPath}/${id}`), { analyticsExcluded: true }));
  await assertFails(update(ref(owner, `${logsPath}/${id}`), { customerName: "Someone Else" }));
});

test("a branch manager cannot write outside the branch they run", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();

  // Menu edits and roster changes are scoped to the manager's own branch.
  await assertFails(set(ref(manager, `${companyId}/branches/${otherBranchId}/categories/Drinks/tea`), {
    name: "Tea",
    price: 90,
  }));
  await assertFails(set(ref(manager, `${companyId}/branches/${otherBranchId}/users/new-hire`), {
    uid: "new-hire",
    role: "staff",
  }));
  // Another company is out of reach entirely.
  await assertFails(get(ref(manager, "company-someone-else/branches/branch-x/categories")));
  // Branch control stays with the company owner.
  await assertFails(remove(ref(manager, `${companyId}/branches/${otherBranchId}`)));

  // The same write inside their own branch is fine, which is what makes the
  // denials above about scope rather than about the operation.
  await assertSucceeds(set(ref(manager, `${branchPath}/categories/Drinks/tea`), {
    name: "Tea",
    price: 90,
  }));
});

test("a branch manager cannot promote anyone to manager", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const owner = testEnv.authenticatedContext(managerUid).database();
  const newbie = "brand-new-staff";

  // Managers add staff only — never a peer, and never the owner slot.
  await assertSucceeds(set(ref(manager, `${branchPath}/users/${newbie}`), {
    uid: newbie,
    role: "staff",
  }));
  await assertFails(set(ref(manager, `${branchPath}/users/promoted`), {
    uid: "promoted",
    role: "manager",
  }));
  await assertFails(set(ref(manager, `${branchPath}/users/fake-owner`), {
    uid: "fake-owner",
    role: "owner",
  }));
  await assertSucceeds(set(ref(owner, `${branchPath}/users/appointed`), {
    uid: "appointed",
    role: "manager",
  }));
});

test("replacing a manager means stepping the role down, not just moving the pointer", async () => {
  const owner = testEnv.authenticatedContext(managerUid).database();
  const outgoing = testEnv.authenticatedContext(branchManagerUid).database();

  // The accounts row provisioning writes, which is what loadAccessContext() reads
  // as accountRole.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `accounts/${branchManagerUid}`), {
      uid: branchManagerUid,
      companyId,
      activeBranchId: branchId,
      role: "manager",
    });
  });

  // In post, they run the branch — the menu is theirs to edit.
  await assertSucceeds(
    set(ref(outgoing, `${branchPath}/categories/Drinks/tea`), { name: "Tea", price: 90 })
  );

  // Handing branchProfile/managerUid to a successor is NOT the handover. Menu
  // writes read the membership row's role, so moving the pointer alone leaves the
  // outgoing manager editing the menu, the settings and the thresholds.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `${branchPath}/branchProfile/managerUid`), "successor-uid");
  });
  await assertSucceeds(
    set(ref(outgoing, `${branchPath}/categories/Drinks/juice`), { name: "Juice", price: 80 })
  );

  // The step-down itself has to be writable by the owner, including the accounts
  // row the owner is not permitted to read back.
  await assertSucceeds(
    update(ref(owner, `${branchPath}/users/${branchManagerUid}`), { role: "staff" })
  );
  await assertSucceeds(
    update(ref(owner, `${companyId}/users/${branchManagerUid}`), {
      role: "staff",
      companyRole: "staff",
    })
  );
  await assertSucceeds(update(ref(owner, `accounts/${branchManagerUid}`), { role: "staff" }));

  // Only now is the branch closed to them, and the staff-adding power they held a
  // moment ago goes with it.
  await assertFails(
    set(ref(outgoing, `${branchPath}/categories/Drinks/soda`), { name: "Soda", price: 70 })
  );
  await assertFails(
    set(ref(outgoing, `${branchPath}/users/extra`), { uid: "extra", role: "staff" })
  );

  // Which leaves the outgoing manager as a normal member: they can still read
  // their own row, so this is a step-down rather than an eviction.
  await assertSucceeds(get(ref(outgoing, `${branchPath}/users/${branchManagerUid}`)));
});

test("a branch with no manager hands every management action back to its owner", async () => {
  const owner = testEnv.authenticatedContext(managerUid).database();
  const neighbouringManager = testEnv.authenticatedContext(branchManagerUid).database();
  const unmanaged = `${companyId}/branches/${otherBranchId}`;

  // IT Park has an ownerUid and no managerUid. That is both what a branch looks
  // like at onboarding and what removing its only manager leaves behind, so the
  // owner has to be able to run it directly — otherwise a manager leaving strands
  // the branch until the owner can be talked through the database console.
  await assertSucceeds(
    set(ref(owner, `${unmanaged}/categories/Drinks/tea`), { name: "Tea", price: 90 })
  );
  await assertSucceeds(
    set(ref(owner, `${unmanaged}/appSettings`), { businessName: "IT Park" })
  );
  await assertSucceeds(
    set(ref(owner, `${unmanaged}/users/new-hire`), { uid: "new-hire", role: "staff" })
  );
  await assertSucceeds(
    set(ref(owner, `${unmanaged}/kiosks/kiosk-1`), {
      kioskUid: "kiosk-1",
      name: "Counter",
      isActive: true,
    })
  );

  // Appointing a manager is how the fallback ends, so that write has to work too.
  await assertSucceeds(
    set(ref(owner, `${unmanaged}/branchProfile/managerUid`), "successor-uid")
  );

  // The fallback goes to the company owner, not to whoever else happens to hold a
  // manager role somewhere nearby. The other branch's manager inherits nothing.
  await assertFails(
    set(ref(neighbouringManager, `${unmanaged}/categories/Drinks/tea`), { name: "Tea", price: 90 })
  );
  await assertFails(
    set(ref(neighbouringManager, `${unmanaged}/users/new-hire`), { uid: "new-hire", role: "staff" })
  );
  await assertFails(
    set(ref(neighbouringManager, `${unmanaged}/kiosks/kiosk-2`), {
      kioskUid: "kiosk-2",
      name: "Counter",
      isActive: true,
    })
  );
});

test("removing a member clears the account record, which is the only record a delete can read from", async () => {
  const owner = testEnv.authenticatedContext(managerUid).database();

  // Deleting is the one write where newData is empty, so the ownership check has
  // nothing to look the company up from. An owner deleting another member's
  // record was refused, and the removal code swallowed the refusal — leaving the
  // person's next sign-in still carrying a role for a company they had been taken
  // out of.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `accounts/${staffUid}`), {
      uid: staffUid,
      companyId,
      activeBranchId: branchId,
      role: "staff",
    });
  });

  await assertSucceeds(remove(ref(owner, `accounts/${staffUid}`)));

  // Still confined to the owner's own company: an account belonging to a company
  // this person has no standing in is out of reach.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), "accounts/someone-else"), {
      uid: "someone-else",
      companyId: "company-someone-else",
      activeBranchId: "branch-someone-else",
      role: "staff",
    });
  });
  await assertFails(remove(ref(owner, "accounts/someone-else")));
});

test("a signed-in stranger cannot write itself into a company or branch", async () => {
  const stranger = testEnv.authenticatedContext(outsiderUid).database();

  // Self-insertion is the escalation path, not a convenience: the company-wide
  // .read trusts the member list, so a forged row buys read access to every
  // branch the company owns, and a forged branch row named 'manager' buys the
  // menu with it. Membership has to come from someone who already has standing.
  await assertFails(set(ref(stranger, `${companyId}/users/${outsiderUid}`), {
    uid: outsiderUid,
    email: "x@example.com",
    companyId,
    companyRole: "manager",
    branchIds: { [branchId]: true },
  }));
  await assertFails(set(ref(stranger, `${branchPath}/users/${outsiderUid}`), {
    uid: outsiderUid,
    role: "manager",
  }));
  await assertFails(set(ref(stranger, `${branchPath}/users/${outsiderUid}`), {
    uid: outsiderUid,
    role: "staff",
  }));
  // Nor can it mint an account record, which is what resolves companyId at login.
  await assertFails(set(ref(stranger, `accounts/${outsiderUid}`), {
    uid: outsiderUid,
    companyId,
    activeBranchId: branchId,
    role: "manager",
  }));
  await assertFails(set(ref(stranger, `${branchPath}/branchProfile/managerUid`), outsiderUid));
  await assertFails(get(ref(stranger, `${branchPath}/categories`)));

  // Creating a company of its own is still allowed — that is onboarding, and the
  // company does not exist yet, so nothing is overwritten.
  await assertSucceeds(set(ref(stranger, "company-stranger-own/companyProfile"), {
    companyId: "company-stranger-own",
    companyName: "Stranger Co",
    ownerUids: { [outsiderUid]: true },
  }));
});

test("members maintain their own records without being able to re-rank themselves", async () => {
  const staff = testEnv.authenticatedContext(staffUid).database();
  const manager = testEnv.authenticatedContext(branchManagerUid).database();

  // Sign-in refreshes the profile row, so self-service has to keep working.
  await assertSucceeds(update(ref(staff, `${companyId}/users/${staffUid}`), {
    displayName: "Renamed",
  }));
  await assertSucceeds(update(ref(staff, `${branchPath}/users/${staffUid}`), {
    uid: staffUid,
    role: "staff",
    addedAt: 1,
  }));

  // But the role itself is not theirs to raise, in either record.
  await assertFails(update(ref(staff, `${companyId}/users/${staffUid}`), {
    companyRole: "manager",
  }));
  await assertFails(set(ref(staff, `${branchPath}/users/${staffUid}`), {
    uid: staffUid,
    role: "owner",
  }));
  await assertFails(set(ref(staff, `${branchPath}/users/${staffUid}`), {
    uid: staffUid,
    role: "manager",
  }));

  // A manager may not promote itself either.
  await assertFails(set(ref(manager, `${branchPath}/users/${branchManagerUid}`), {
    uid: branchManagerUid,
    role: "owner",
  }));
});

test("a branch manager can take staff off the roster but not the owner", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const hire = "short-lived-hire";

  await assertSucceeds(set(ref(manager, `${branchPath}/users/${hire}`), {
    uid: hire,
    role: "staff",
  }));
  await assertSucceeds(set(ref(manager, `${companyId}/users/${hire}`), {
    uid: hire,
    email: "hire@example.com",
    companyId,
    companyRole: "staff",
    branchIds: { [branchId]: true },
  }));

  // removeTeamMember() clears the branch row, the company profile and the account
  // record; leaving any one of them behind strands a half-removed member.
  await assertSucceeds(remove(ref(manager, `${branchPath}/users/${hire}`)));
  await assertSucceeds(remove(ref(manager, `${companyId}/users/${hire}`)));

  // The owner is not a manager's to remove, on either node.
  await assertFails(remove(ref(manager, `${branchPath}/users/${managerUid}`)));
  await assertFails(remove(ref(manager, `${companyId}/users/${managerUid}`)));
});

test("the rules mirrored into the kiosk repo match the ones actually deployed", () => {
  // The kiosk project keeps a copy of the policy at its root so it documents the
  // rules it runs under. That copy is what drift made dangerous: it sat for two
  // weeks carrying a policy where any signed-in account could write itself onto
  // any branch as a manager, while the deployed rules had outgrown it. Nothing
  // referenced it, so nothing caught it.
  //
  // It is checked rather than trusted, and it is compared byte for byte because
  // the file is deployed verbatim - a reformat would still be the same policy,
  // but it would also be an unexplained diff in a security file.
  const mirror = fs.readFileSync(path.resolve("../database.rules.json"), "utf8");
  assert.equal(
    mirror,
    rules,
    "MenuApplication VsCode /database.rules.json no longer matches the deployed policy"
  );
});

// ===== Analytics exclusions =====
//
// Excluding an order flags it as not counting toward the analytics roll-up. It
// is an accounting adjustment, not an edit to the order: the order stays in the
// ledger, the flag records who set it and why, and it can be lifted again.
//
// It lives in its own node rather than on the order itself. Writing it onto the
// order would mean relaxing the .write rule on /logs, which currently allows
// exactly one thing - a kiosk creating an order that does not exist yet. That
// rule is what makes kiosk orders immutable, and an adjustment is not worth
// trading it away.

const exclusion = (orderId, uid) => ({
  excluded: true,
  reason: "Duplicate order",
  at: { ".sv": "timestamp" },
  by: uid,
});

test("a branch manager can flag an order out of the analytics roll-up", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const owner = testEnv.authenticatedContext(managerUid).database();

  await assertSucceeds(set(ref(manager, `${branchPath}/analyticsExclusions/ORDER-1`), exclusion("ORDER-1", branchManagerUid)));
  await assertSucceeds(set(ref(owner, `${branchPath}/analyticsExclusions/ORDER-2`), exclusion("ORDER-2", managerUid)));

  // The flag can be lifted, which is what makes it safe to set.
  await assertSucceeds(remove(ref(manager, `${branchPath}/analyticsExclusions/ORDER-1`)));
});

test("staff can see the flags but not set them", async () => {
  const staff = testEnv.authenticatedContext(staffUid).database();
  const manager = testEnv.authenticatedContext(branchManagerUid).database();

  await assertSucceeds(get(ref(staff, `${branchPath}/analyticsExclusions`)));

  // Excluding flatters the numbers, so it stays with the roles that answer for them.
  await assertFails(set(ref(staff, `${branchPath}/analyticsExclusions/ORDER-3`), exclusion("ORDER-3", staffUid)));
  await assertFails(remove(ref(staff, `${branchPath}/analyticsExclusions/ORDER-1`)));

  // And the manager's own flag is still there afterwards, which is what makes the
  // denial above about permissions rather than about the node being empty.
  await assertSucceeds(set(ref(manager, `${branchPath}/analyticsExclusions/ORDER-1`), exclusion("ORDER-1", branchManagerUid)));
  await assertSucceeds(get(ref(staff, `${branchPath}/analyticsExclusions/ORDER-1`)));
});

test("an exclusion cannot be forged or left unattributed", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const outsider = testEnv.authenticatedContext(outsiderUid).database();

  await assertFails(set(ref(outsider, `${branchPath}/analyticsExclusions/ORDER-4`), exclusion("ORDER-4", outsiderUid)));

  // `by` has to be the writer, so an exclusion always names the account that
  // decided it rather than whatever the client felt like putting there.
  await assertFails(set(ref(manager, `${branchPath}/analyticsExclusions/ORDER-5`), {
    excluded: true,
    reason: "Duplicate order",
    at: { ".sv": "timestamp" },
    by: managerUid,
  }));
  await assertFails(set(ref(manager, `${branchPath}/analyticsExclusions/ORDER-6`), {
    excluded: true,
    reason: "",
    at: { ".sv": "timestamp" },
    by: branchManagerUid,
  }));
  await assertFails(set(ref(manager, `${branchPath}/analyticsExclusions/ORDER-7`), {
    excluded: true,
    by: branchManagerUid,
  }));
  await assertFails(set(ref(manager, `${branchPath}/analyticsExclusions/ORDER-8`), {
    reason: "Duplicate order",
    at: { ".sv": "timestamp" },
    by: branchManagerUid,
  }));
});

test("exclusions cannot be set from outside the branch that owns the order", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const staff = testEnv.authenticatedContext(staffUid).database();

  // A branch manager has no standing in the branch next door: they run one
  // branch, and the neighbouring one has a different ownerUid and no managerUid
  // pointing at them.
  await assertFails(set(
    ref(manager, `${companyId}/branches/${otherBranchId}/analyticsExclusions/ORDER-9`),
    exclusion("ORDER-9", branchManagerUid)
  ));
  await assertFails(set(
    ref(staff, `${companyId}/branches/${otherBranchId}/analyticsExclusions/ORDER-9`),
    exclusion("ORDER-9", staffUid)
  ));

  // Neither of them can reach into a company they are not part of.
  await assertFails(set(
    ref(staff, `${companyId}/branches/${branchId}/analyticsExclusions/ORDER-9-own`),
    exclusion("ORDER-9-own", staffUid)
  ));
});

test("only a company that does not exist yet is open to anyone", async () => {
  // This documents the onboarding bootstrap rather than a gap in the exclusions
  // node, because the first assertion here is the reason the second company in
  // the previous test had to be one that already exists. $companyId/.write is
  // gated on `!data.exists()`, so a signed-in account may populate the tree of a
  // company id nobody has claimed — that is how a new owner creates their own
  // company, since there is no server to do it for them.
  //
  // It is bounded rather than open: companyProfile/.validate requires the writer
  // to list themselves in ownerUids, so an account can only create a company it
  // owns, and the moment the company exists the clause stops applying. Closing
  // it entirely needs a Cloud Function on company creation, which this project
  // does not have.
  const stranger = testEnv.authenticatedContext(outsiderUid).database();
  const unclaimed = "company-nobody-has-claimed";

  await assertSucceeds(set(
    ref(stranger, `${unclaimed}/branches/branch-x/analyticsExclusions/A`),
    exclusion("A", outsiderUid)
  ));

  // The same write against a company that exists is refused.
  await assertFails(set(
    ref(stranger, `${companyId}/branches/${branchId}/analyticsExclusions/A`),
    exclusion("A", outsiderUid)
  ));
});

test("flagging an order leaves the order record itself untouched", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();
  const id = "123e4567-e89b-12d3-a456-426614174020";
  const logsPath = `${branchPath}/logs`;

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `${logsPath}/${id}`), validOrder(companyKioskUid, id));
  });

  // The point of the separate node: the adjustment succeeds without the order
  // becoming writable. Both halves are asserted, because a change that made the
  // second one pass would be the wrong fix.
  await assertSucceeds(set(ref(manager, `${branchPath}/analyticsExclusions/${id}`), exclusion(id, branchManagerUid)));
  await assertFails(update(ref(manager, `${logsPath}/${id}`), { total: 1 }));
  await assertFails(update(ref(manager, `${logsPath}/${id}`), { analyticsExcluded: true }));
});

// ===== Kiosk management =====
//
// Running a branch includes managing the tablets in it, so MANAGER_CAPS carries
// MANAGE_KIOSKS and the Kiosks page is gated on it. The rules only ever allowed
// the branch owner, which made every control on that page fail for the role the
// page was opened for: register, enable, disable and deregister all write through
// at least one owner-only path.

const newKioskUid = "branch-manager-enrolled-kiosk";

test("a branch manager can register and manage a kiosk in their own branch", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();

  // 1. the branch's kiosk record
  await assertSucceeds(set(ref(manager, `${branchPath}/kiosks/${newKioskUid}`), {
    kioskUid: newKioskUid,
    name: "Front Counter",
    isActive: true,
  }));
  // 2. toggling it, which is the same node
  await assertSucceeds(update(ref(manager, `${branchPath}/kiosks/${newKioskUid}`), {
    isActive: false,
  }));
  // 3. the company's enrollment record
  await assertSucceeds(set(ref(manager, `${companyId}/kioskEnrollments/${newKioskUid}`), {
    kioskUid: newKioskUid,
    companyId,
    branchId,
    name: "Front Counter",
    isActive: true,
    registeredAt: 1,
  }));
  // 4. the root pointer the device reads before it knows its company
  await assertSucceeds(set(ref(manager, `kioskEnrollments/${newKioskUid}`), {
    companyId,
    branchId,
    isActive: true,
    updatedAt: 1,
  }));
});

test("staff cannot manage kiosks", async () => {
  const staff = testEnv.authenticatedContext(staffUid).database();

  await assertFails(set(ref(staff, `${branchPath}/kiosks/${newKioskUid}`), {
    kioskUid: newKioskUid,
    name: "Front Counter",
    isActive: true,
  }));
  await assertFails(set(ref(staff, `${companyId}/kioskEnrollments/${newKioskUid}`), {
    kioskUid: newKioskUid,
    companyId,
    branchId,
    name: "Front Counter",
    isActive: true,
    registeredAt: 1,
  }));
});

test("a branch manager cannot register a kiosk into another branch", async () => {
  const manager = testEnv.authenticatedContext(branchManagerUid).database();

  // Managing devices is scoped to the branch being managed. The neighbouring
  // branch has a different ownerUid and no managerUid pointing at this account.
  await assertFails(set(ref(manager, `${companyId}/branches/${otherBranchId}/kiosks/${newKioskUid}`), {
    kioskUid: newKioskUid,
    name: "Smuggled",
    isActive: true,
  }));
  await assertFails(set(ref(manager, `${companyId}/kioskEnrollments/${newKioskUid}`), {
    kioskUid: newKioskUid,
    companyId,
    branchId: otherBranchId,
    name: "Smuggled",
    isActive: true,
    registeredAt: 1,
  }));
});
