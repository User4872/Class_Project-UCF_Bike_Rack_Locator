const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const fs = require("fs");
const { parse } = require("csv-parse/sync");

initializeApp({
  credential: cert(require("./serviceAccountKey.json")),
});
const db = getFirestore();

async function main()
{
  const rows = parse(fs.readFileSync("UCF_BIKE_RACK.csv"),
  {
    columns: true,
    skip_empty_lines: true,
    bom: true,
    trim: true,
  }
  );

  const existing = new Set((await db.collection("bike_racks").select().get()).docs.map((d) => d.id));

  let batch = db.batch();
  let count = 0, created = 0, updated = 0, skipped = 0;

  for (const row of rows)
  {
    const latitude = parseFloat(row.latitude);
    const longitude = parseFloat(row.longitude);

    if (isNaN(latitude) || isNaN(longitude) || latitude < 28 || latitude > 29 || longitude > -81 || longitude < -82)
    {
      console.warn(`Skipping "${row.name}" (${row.feature_ID}): bad coordinates ${row.latitude}, ${row.longitude}`);
      skipped++;
      continue;
    }

    const id = String(row.feature_ID).replace(/[\/\s]/g, "_");
    const ref = db.collection("bike_racks").doc(id);

    const data =
    {
    feature_ID: row.feature_ID, name: row.name, latitude, longitude
    };
    if (!existing.has(id))
    {
      data.currentOccupancy = null;
      data.lastReportedAt = null;
      created++;
    }
    else
    {
      updated++;
    }

    batch.set(ref, data, { merge: true });

    if (++count % 400 === 0)
    {
      await batch.commit();
      batch = db.batch();
    }
  }

  await batch.commit();
  console.log(`Done. Created ${created}, updated ${updated}, skipped ${skipped}.`);
}

main().catch((err) =>
{
  console.error(err);
  process.exit(1);
});
