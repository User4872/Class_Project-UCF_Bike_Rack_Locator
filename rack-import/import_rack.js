const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const fs = require("fs");
const path = require("path");
const { parse } = require("csv-parse/sync");

initializeApp({credential: cert(require("./serviceAccountKey.json")),});
const db = getFirestore();

async function main() {
  const rows = parse(fs.readFileSync(path.join(__dirname, "UCF_Bike_Rack.csv")),
  {
    columns: true,
    skip_empty_lines: true,
    bom: true,
    trim: true,
  });

  let batch = db.batch();
  let count = 0, written = 0, skipped = 0;

  for (const row of rows)
  {
    const latitude = parseFloat(row.latitude);
    const longitude = parseFloat(row.longitude);

    if (isNaN(latitude) || isNaN(longitude) || latitude < 28 || latitude > 29 || longitude > -81
    || longitude < -82)
    {
      console.warn(`Skipping "${row.name}" (${row.feature_ID}):
      bad coordinates ${row.latitude}, ${row.longitude}`);
      skipped++;
      continue;
    }

    const id = String(row.feature_ID).replace(/[\/\s]/g, "_");
    batch.set(
      db.collection("racks").doc(id),
      { feature_ID: row.feature_ID, name: row.name, latitude, longitude },
      { merge: true }
    );
    written++;

    if (++count % 400 === 0)
    {
      await batch.commit();
      batch = db.batch();
    }
  }

  await batch.commit();
  console.log(`Done. Wrote ${written}, skipped ${skipped}.`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});