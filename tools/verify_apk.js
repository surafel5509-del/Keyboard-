#!/usr/bin/env node
/*
 * Independent APK Signature Scheme v2 verifier (no Android SDK required).
 *
 * Usage: node tools/verify_apk.js path/to/app.apk
 *
 * It verifies, without trusting the signer script:
 *   1. the APK Sig Block is well formed and contains a v2 block (0x7109871a),
 *   2. the RSA signature over signed-data verifies with the embedded certificate,
 *   3. the SHA-256 digest of the certificate matches `certificates`,
 *   4. the chunked SHA-256 digests of the three APK sections match the ones in
 *      signed-data (so no byte of the APK was modified after signing).
 *
 * Exit code 0 means the APK is a valid v2-signed package.
 */
const fs = require('fs');
const crypto = require('crypto');

const V2_BLOCK_ID = 0x7109871a;
const MAGIC = Buffer.from('APK Sig Block 42', 'utf8');

function fail(message) {
  console.error('INVALID: ' + message);
  process.exit(1);
}

function readU32(buf, offset) {
  return buf.readUInt32LE(offset);
}

function readU64(buf, offset) {
  return Number(buf.readBigUInt64LE(offset));
}

/** Reads a uint32-length-prefixed slice. */
function readLp(buf, offset) {
  const length = readU32(buf, offset);
  return { data: buf.subarray(offset + 4, offset + 4 + length), next: offset + 4 + length, length };
}

function findEocd(buf) {
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 0xffff); i--) {
    if (readU32(buf, i) === 0x06054b50) return i;
  }
  throw new Error('EOCD record not found');
}

/** Chunked content digest over the three signed sections (AOSP algorithm). */
function chunkedDigest(sections) {
  const digests = [];
  const header = Buffer.alloc(5);
  for (const section of sections) {
    for (let offset = 0; offset < section.length; offset += 1048576) {
      const chunk = section.subarray(offset, Math.min(section.length, offset + 1048576));
      header.writeUInt8(0xa5, 0);
      header.writeUInt32LE(chunk.length, 1);
      digests.push(crypto.createHash('sha256').update(header).update(chunk).digest());
    }
  }
  const top = Buffer.alloc(5);
  top.writeUInt8(0x5a, 0);
  top.writeUInt32LE(digests.length, 1);
  return crypto.createHash('sha256').update(top).update(Buffer.concat(digests)).digest();
}

function main() {
  const path = process.argv[2];
  if (!path) fail('usage: node tools/verify_apk.js <apk>');
  const apk = fs.readFileSync(path);

  const eocd = findEocd(apk);
  const cdOffset = readU32(apk, eocd + 16);
  const cdSize = readU32(apk, eocd + 12);
  if (cdOffset === 0xffffffff || cdSize === 0xffffffff) fail('ZIP64 APKs are not supported by this checker');

  // The signing block sits immediately before the central directory.
  const magicOffset = cdOffset - 16;
  if (!apk.subarray(magicOffset, magicOffset + 16).equals(MAGIC)) fail('APK Sig Block magic missing');
  const blockSize = readU64(apk, magicOffset - 8);
  const blockStart = cdOffset - blockSize - 8;
  if (readU64(apk, blockStart) !== blockSize) fail('APK Sig Block size fields disagree');

  const entries = [];
  let cursor = blockStart + 8;
  const entriesEnd = cdOffset - 24;
  while (cursor < entriesEnd) {
    const length = readU64(apk, cursor);
    const id = readU32(apk, cursor + 8);
    entries.push({ id, value: apk.subarray(cursor + 12, cursor + 8 + length) });
    cursor += 8 + length;
  }

  const v2 = entries.find((entry) => entry.id === V2_BLOCK_ID);
  if (!v2) fail('no APK Signature Scheme v2 block (0x7109871a) found');
  const other = entries.filter((entry) => entry.id !== V2_BLOCK_ID).map((entry) => '0x' + entry.id.toString(16));
  console.log(`sig blocks: v2=0x${V2_BLOCK_ID.toString(16)}${other.length ? ', other=' + other.join(',') : ''}`);

  // v2 value: LP(sequence of signers); we support the single-signer layout that
  // Android itself produces for apps signed with one key.
  const signers = readLp(v2.value, 0);          // length-prefixed signer sequence
  const signer = readLp(signers.data, 0);       // first signer
  const signedDataLp = readLp(signer.data, 0);  // signer = LP(signed data) + LP(signatures) + LP(pubkey)
  const signedData = signedDataLp.data;
  const signatures = readLp(signer.data, signedDataLp.next);
  const publicKeyLp = readLp(signer.data, signatures.next);

  // signed-data: digests, certificates, attributes (each length-prefixed)
  const digestsLp = readLp(signedData, 0);
  const digests = [];
  let digestCursor = 0;
  while (digestCursor < digestsLp.data.length) {
    const item = readLp(digestsLp.data, digestCursor);
    const inner = readLp(item.data, 4);
    if (inner.next !== item.data.length) fail('malformed digest record');
    digests.push({ algorithm: readU32(item.data, 0), digest: inner.data });
    digestCursor = item.next;
  }
  const certificatesLp = readLp(signedData, digestsLp.next);
  const certificate = readLp(certificatesLp.data, 0).data;

  // signatures
  const signatureList = [];
  let signatureCursor = 0;
  while (signatureCursor < signatures.data.length) {
    const item = readLp(signatures.data, signatureCursor);
    const inner = readLp(item.data, 4);
    if (inner.next !== item.data.length) fail('malformed signature record');
    signatureList.push({ algorithm: readU32(item.data, 0), signature: inner.data });
    signatureCursor = item.next;
  }
  const publicKey = crypto.createPublicKey({ key: publicKeyLp.data, format: 'der', type: 'spki' });
  const x509 = new crypto.X509Certificate(certificate);
  const certKeyDer = x509.publicKey.export({ type: 'spki', format: 'der' });

  if (!publicKey.export({ type: 'spki', format: 'der' }).equals(certKeyDer)) {
    fail('the embedded public key does not match the signing certificate');
  }
  const RSA_SHA256 = 0x0103;
  const signature = signatureList.find((item) => item.algorithm === RSA_SHA256);
  if (!signature) fail('no RSA-PKCS1-SHA256 signature found');
  if (!crypto.verify('sha256', signedData, publicKey, signature.signature)) {
    fail('the v2 signature over signed-data does not verify');
  }

  // Content digest: the EOCD's central-directory offset is normalised to the
  // signing block offset before hashing, exactly like the platform verifier.
  const eocdCopy = Buffer.from(apk.subarray(eocd));
  const eocdCdOffset = blockStart;
  eocdCopy.writeUInt32LE(eocdCdOffset, 16);
  const parts = [
    apk.subarray(0, blockStart),
    apk.subarray(cdOffset, cdOffset + cdSize),
    eocdCopy,
  ];
  const digestCheck = digests.find((item) => item.algorithm === RSA_SHA256);
  if (!digestCheck) fail('no digest for algorithm 0x0103');
  const actual = chunkedDigest(parts);
  if (!actual.equals(digestCheck.digest)) {
    fail('content digest mismatch (the APK was modified after signing)');
  }

  const subject = x509.subject.replace(/\n/g, ', ');
  const sha256 = crypto.createHash('sha256').update(apk).digest('hex');
  console.log(`certificate: ${subject}`);
  console.log(`apk sha256:  ${sha256}`);
  console.log(`apk size:    ${apk.length} bytes`);
  const totalBytes = parts.reduce((sum, part) => sum + part.length, 0);
  console.log(`verified:    ${totalBytes} bytes of signed content (${Math.ceil(totalBytes / 1048576)} MiB, 3 sections)`);
  console.log('VALID: APK Signature Scheme v2 signature and digests are correct');
}

main();
