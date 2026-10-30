# RoomLedger

แอป Android สำหรับจัดการห้องเช่า ระยะที่ 1 พัฒนาด้วย Kotlin, Jetpack Compose, Material 3, Room และ MVVM ข้อมูลห้องเก็บไว้ใน SQLite บนอุปกรณ์และใช้งานได้โดยไม่ต้องเชื่อมต่ออินเทอร์เน็ต

## ความสามารถ

- แดชบอร์ดภาษาไทย แสดงจำนวนห้องที่เปิดใช้งานและค่าเช่ารวมต่อเดือน
- เพิ่มและแก้ไขชื่อห้อง ค่าเช่ารายเดือน อัตราค่าน้ำ และอัตราค่าไฟ
- เปิดหรือปิดใช้งานห้องโดยเก็บรายการไว้ในฐานข้อมูล
- แสดงข้อความเมื่อยังไม่มีข้อมูลห้องพัก
- รองรับธีมสว่างและมืดตามการตั้งค่าระบบ
- ตรวจสอบข้อมูลก่อนบันทึก และจัดเก็บจำนวนเงินเป็น `Long` หน่วยสตางค์
- ใช้ UUID เป็นรหัสห้อง พร้อมเวลา `createdAtMillis` และ `updatedAtMillis`
- บันทึกเลขมิเตอร์น้ำและไฟแยกรอบบิลรายเดือน พร้อมแนะนำเลขครั้งก่อนจากรอบก่อนหน้า
- คำนวณค่าน้ำ ค่าไฟ และยอดรวมจากค่าเช่า โดยเก็บเงินเป็นจำนวนเต็มสตางค์
- เก็บ snapshot ค่าเช่าและอัตราค่าสาธารณูปโภคไว้ในบิล พร้อมดูประวัติและรายละเอียดบิล
- ติดตามสถานะชำระเงิน และยืนยันการกระทบยอดอย่างชัดเจนก่อนแก้บิลที่ชำระแล้ว
- รองรับการปรับหน่วยเมื่อเปลี่ยนหรือมิเตอร์หมุน และเตือนเมื่อแก้ไขรอบย้อนหลัง
- เลือกรอบบิลจากหน้ารายการห้อง แสดงเดือนภาษาไทยปี พ.ศ. พร้อมสถานะห้องที่บันทึกแล้ว/ค้าง
- แยกหน้าบันทึกมิเตอร์และรายละเอียดบิลให้ใช้งานบนมือถือได้สะดวกขึ้น
- บันทึกรับชำระบางส่วนได้หลายครั้ง พร้อมประวัติและรายการกลับรายการที่ตรวจสอบย้อนหลังได้
- ปิดบิลเพื่อยืนยันยอด และยกยอดค้างผ่านบัญชีหนี้ที่อ้างอิงบิลต้นทางโดยไม่บวกซ้ำ
- บันทึกหรือแชร์ภาพ PNG รายละเอียดบิลผ่านคลังภาพและเมนูแชร์ของ Android โดยไม่ต้องใช้อินเทอร์เน็ต

## เทคโนโลยีและโครงสร้าง

- Kotlin 2.0.21, Gradle Kotlin DSL และ Android Gradle Plugin 8.7.3
- Jetpack Compose, Material 3 และ Lifecycle ViewModel/StateFlow
- Room 2.6.1 สำหรับ SQLite แบบออฟไลน์
- Repository interface คั่นระหว่าง ViewModel กับฐานข้อมูล เพื่อรองรับแหล่งสำรองข้อมูลในอนาคต โดยยังไม่มีการเชื่อม Firebase

`presentation` มีหน้าจอและ ViewModel, `domain` มีโมเดล/กฎตรวจสอบ/สัญญา Repository และ `data` มี Room DAO, entity และการแปลงข้อมูล

## เริ่มต้นพัฒนา

ต้องติดตั้ง JDK 17 ขึ้นไปและ Android SDK Platform 35 พร้อม Android Build Tools ที่ Gradle ต้องใช้ เปิดโปรเจกต์นี้ใน Android Studio หรือรันจาก root:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

หาก Android SDK อยู่ในตำแหน่งที่ไม่ใช่ค่ามาตรฐาน ให้กำหนด `ANDROID_HOME` หรือสร้าง `local.properties` ในเครื่องนักพัฒนาโดยกำหนด `sdk.dir` (ไฟล์นี้ถูก ignore และห้าม commit)

## ทดสอบ

Unit tests ครอบคลุมการแปลงเงินบาทเป็นสตางค์ การตรวจสอบข้อมูล และการจัดรูปแบบจำนวนเงิน รันด้วย `./gradlew testDebugUnitTest`

Phase 2 เพิ่มการคำนวณบิล การตรวจเลขมิเตอร์และ overflow ใน unit tests และ migration ฐานข้อมูลจาก version 1 ไป version 2 โดยไม่ลบข้อมูลห้องเดิม

Phase 3 เพิ่ม migration แบบไม่ทำลายข้อมูลจาก Room version 2 ไป 3 สำหรับการรับชำระ บัญชีจัดสรรยอดและยอดยกไป การทดสอบ migration และ workflow การเงินอยู่ใน instrumentation tests; รันบนอุปกรณ์หรือ emulator ด้วย:

```bash
./gradlew connectedDebugAndroidTest
```

ทดสอบ unit tests และสร้างแอปสำหรับ debug ด้วยคำสั่งด้านบน (`./gradlew testDebugUnitTest` และ `./gradlew assembleDebug`) ภาพบิลจะถูกบันทึกใน `Pictures/RoomLedger` และส่งต่อให้แอปอื่นด้วย Android Sharesheet

## สำรองและกู้คืนข้อมูล

เปิด **ตั้งค่า · สำรองและกู้คืนข้อมูล** จากหน้าแดชบอร์ด เลือก **สำรองข้อมูล** เพื่อบันทึกไฟล์ `.rlbk` ผ่าน Android Storage Access Framework และตั้งรหัสผ่านอย่างน้อย 8 ตัวอักษร ไฟล์เข้ารหัสด้วย AES-256-GCM โดยอนุมานกุญแจจากรหัสผ่าน ผู้ใช้ต้องเก็บรหัสผ่านเอง แอปไม่บันทึกรหัสผ่านไว้

กู้คืนโดยเลือกไฟล์สำรองและใส่รหัสผ่าน แอปจะแสดงจำนวนห้อง มิเตอร์ บิล และรายการรับเงินให้ตรวจสอบก่อนยืนยัน การกู้คืนจะแทนที่ข้อมูลในเครื่องทั้งหมด ใช้เฉพาะไฟล์ที่ตรวจสอบความถูกต้องและเข้ากันได้กับ Room schema version 3 แล้ว การแทนที่ทำใน transaction เดียว หากล้มเหลวฐานข้อมูลเดิมจะ rollback กลับมา

ทดสอบสำรอง/กู้คืน การตรวจ passphrase และไฟล์เสียหาย รวมถึง rollback เมื่อ restore ถูกขัดจังหวะด้วย instrumentation tests:

```bash
./gradlew connectedDebugAndroidTest
```

## Google Sign-in และ Cloud Backup (Phase 4B)

ฟังก์ชัน Google Sign-in/Firestore เป็นทางเลือก: หากไม่มีไฟล์ Firebase ในเครื่อง แอปยัง build ได้และจัดการข้อมูลใน Room แบบออฟไลน์ได้ตามปกติ การสำรอง Cloud ใช้ไฟล์เข้ารหัส Phase 4A ก่อนแบ่งเป็นชิ้นละไม่เกิน 512 KiB, จำกัดไฟล์ Cloud ไม่เกิน 20 MiB และเก็บเฉพาะไฟล์เข้ารหัสใน Firestore ภายใต้ UID ของผู้ใช้ ระบบแสดงเฉพาะ manifest ที่ COMPLETE และตรวจ checksum ของทุกชิ้นก่อนแสดงหรือกู้คืน

รหัสผ่านสำรองจะถามใหม่ทุกครั้งและไม่ถูกเก็บในแอปหรืออัปโหลด การสำรองอัตโนมัติ/WorkManager จึงปิดไว้ เพราะแอปไม่มี key store สำหรับ passphrase ที่ผู้ใช้ตั้งและจะไม่เก็บรหัสผ่านแบบถอดกลับได้เพื่อใช้งานเบื้องหลัง การสำรอง Cloud เป็นการกดสั่งเอง และสามารถลองซ้ำได้เมื่อเครือข่ายขัดข้อง

### ตั้งค่า Firebase ในเครื่องนักพัฒนา

1. สร้าง Firebase project และเพิ่ม Android app ด้วย package name `com.pondktpprint.roomledger` ใน Firebase Console
2. ดาวน์โหลด `google-services.json` ของ Android app และวางไว้ที่ `app/google-services.json` ไฟล์นี้ถูก ignore โดย Git และห้าม commit
3. ใน **Authentication → Sign-in method** เปิด **Google** และกำหนด support email
4. เพิ่ม SHA-1 certificate fingerprint ของ build ที่ใช้ใน **Project settings → Your apps → SHA certificate fingerprints** ตรวจค่า debug/release ด้วย `./gradlew signingReport` แล้วรอให้การตั้งค่า OAuth อัปเดต
5. เปิด **Firestore Database** ใน Native mode เลือก region และใช้ Spark plan ได้ โค้ดอ่าน Web OAuth client ID จาก resource `default_web_client_id` ที่ Google Services plugin สร้างจากไฟล์ Firebase ไม่ต้องคัดลอกหรือใส่ client ID ใน source code
6. ติดตั้ง rules ใน `firestore.rules` ผ่าน **Firestore Database → Rules** ใน Firebase Console แล้วกด Publish ก่อนใช้งาน Cloud Backup ห้ามเปิด Test Mode หรือ public access
7. Sync Gradle และติดตั้งแอปใหม่เพื่อให้ Google Services plugin อ่าน config ในเครื่อง หากไม่มีไฟล์ดังกล่าว plugin จะไม่ถูก apply และหน้าจอจะแสดงวิธีตั้งค่า โดยข้อมูล Room ยังคงใช้งานได้

โครงสร้าง Cloud ใช้ `users/{uid}/backups/{backupId}` และ subcollection `chunks` ไฟล์แต่ละเวอร์ชันมี ID เฉพาะเพื่อป้องกันการเขียนทับข้ามอุปกรณ์ ระบบตรวจและเก็บสามเวอร์ชันล่าสุดที่สมบูรณ์ ส่วน upload ที่ค้าง/ล้มเหลวจะไม่ถูกแสดงและจะล้างเมื่อเก่ากว่า 24 ชั่วโมง การกู้คืนต้องลงชื่อเข้าใช้ เลือกเวอร์ชัน ใส่ passphrase ตรวจ preview และยืนยันการแทนที่ข้อมูลในเครื่อง

Firebase Emulator ไม่ได้ตั้งค่าใน repository นี้ การทดสอบ unit ครอบคลุมการแบ่ง/ประกอบ chunk, checksum, การขาดหาย, สถานะ incomplete, retry และการเก็บสามเวอร์ชัน; tests ของ Phase 4A ครอบคลุม passphrase ผิด, integrity, rollback และการทำงานของ Room โดยไม่ต้องลงชื่อเข้าใช้
