#!/usr/bin/env node
/*
 * Minimal, dependency-light APK Signature Scheme v2 signer.
 *
 * The project is built without the Android SDK in this environment, so the
 * signed APK is produced by this script. It implements the format documented in
 * https://source.android.com/docs/security/features/apksigning/v2
 *
 * Usage:
 *   node tools/sign_v2.js <unsigned.apk> <keystore.jks> <storepass> [out.apk]
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const SIG_BLOCK_MAGIC = Buffer.from('APK Sig Block 42', 'utf8');
const V2_BLOCK_ID = 0x7109871a;
const CHUNK = 1024 * 1024;
const ALG_RSA_PKCS1_SHA256 = 0x0103;

function u32(value) {
  const b = Buffer.alloc(4);
  b.writeUInt32LE(value >>> 0, 0);
  return b;
}
function u64(value) {
  const b = Buffer.alloc(8);
  b.writeBigUInt64LE(BigInt(value), 0);
  return b;
}
function lp(buf) {
  return Buffer.concat([u32(buf.length), buf]);
}

function findEocd(buf) {
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 0xffff); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) return i;
  }
  throw new Error('EOCD not found');
}

function computeContentDigest(entries, centralDir, eocd, algorithm) {
  const sections = [entries, centralDir, eocd];
  const hashes = [];
  for (const section of sections) {
    for (let off = 0; off < section.length; off += CHUNK) {
      const chunk = section.subarray(off, Math.min(off + CHUNK, section.length));
      const h = crypto.createHash(algorithm);
      h.update(Buffer.from([0xa5]));
      h.update(u32(chunk.length));
      h.update(chunk);
      hashes.push(h.digest());
    }
  }
  const top = crypto.createHash(algorithm);
  top.update(Buffer.from([0x5a]));
  top.update(u32(hashes.length));
  for (const h of hashes) top.update(h);
  return top.digest();
}

function readKeystore(keystorePath, storepass) {
  // Convert JKS -> PEM through the same code path used by apk_sign_ts.
  const { convertToPEM } = require(path.join(__dirname, 'node_modules', 'apk_sign_ts'));
  return convertToPEM(new Uint8Array(fs.readFileSync(keystorePath)), storepass, 'jks');
}

async function main() {
  const [, , apkPath, ksPath, storepass, outArg] = process.argv;
  if (!apkPath || !ksPath || !storepass) {
    console.error('usage: sign_v2.js <apk> <keystore.jks> <storepass> [out.apk]');
    process.exit(2);
  }
  const outPath = outArg || apkPath.replace(/\.apk$/, '-signed.apk');
  const apk = fs.readFileSync(apkPath);

  const eocdOffset = findEocd(apk);
  const cdSize = apk.readUInt32LE(eocdOffset + 12);
  const cdOffset = apk.readUInt32LE(eocdOffset + 16);
  if (cdOffset + cdSize !== eocdOffset) {
    throw new Error('unexpected ZIP layout: central directory is not contiguous with EOCD');
  }

  const { privateKey, certificate } = await readKeystore(ksPath, storepass);
  const keyObject = crypto.createPrivateKey(privateKey);
  const certDer = new crypto.X509Certificate(certificate).raw;
  const publicKeyDer = crypto.createPublicKey(certificate).export({ type: 'spki', format: 'der' });

  const entries = apk.subarray(0, cdOffset);
  const centralDir = apk.subarray(cdOffset, eocdOffset);
  const eocd = Buffer.from(apk.subarray(eocdOffset));
  // For the digest, the central directory offset must point at the start of the
  // APK Signing Block, which is exactly where the central directory used to be.
  eocd.writeUInt32LE(cdOffset, 16);

  const digest = computeContentDigest(entries, centralDir, eocd, 'sha256');

  const digestRecord = Buffer.concat([u32(ALG_RSA_PKCS1_SHA256), digest]);
  const digestsField = lp(lp(digestRecord));
  const certsField = lp(lp(certDer));
  const attrsField = lp(Buffer.alloc(0));
  const signedData = Buffer.concat([digestsField, certsField, attrsField]);

  const signature = crypto.sign('sha256', signedData, keyObject);
  const sigRecord = Buffer.concat([u32(ALG_RSA_PKCS1_SHA256), signature]);
  const signaturesField = lp(lp(sigRecord));
  const publicKeyField = lp(publicKeyDer);

  const signer = Buffer.concat([lp(signedData), signaturesField, publicKeyField]);
  const v2Value = lp(lp(signer));

  const pair = Buffer.concat([u64(4 + v2Value.length), u32(V2_BLOCK_ID), v2Value]);
  const blockSize = pair.length + 8 + SIG_BLOCK_MAGIC.length;
  const signingBlock = Buffer.concat([u64(blockSize), pair, u64(blockSize), SIG_BLOCK_MAGIC]);

  const newCdOffset = cdOffset + signingBlock.length;
  const newEocd = Buffer.from(apk.subarray(eocdOffset));
  newEocd.writeUInt32LE(newCdOffset, 16);

  const signed = Buffer.concat([
    apk.subarray(0, cdOffset),
    signingBlock,
    apk.subarray(cdOffset, eocdOffset),
    newEocd,
  ]);
  fs.writeFileSync(outPath, signed);
  console.log(`signed ${path.basename(apkPath)} -> ${path.basename(outPath)} (${signed.length} bytes)`);
}

main().catch((err) => {
  console.error('signing failed:', err.message);
  process.exit(1);
});
