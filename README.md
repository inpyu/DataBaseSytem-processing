# 📘 중앙대학교 25-1 데이터베이스시스템 과제 (1차 + 2차 통합)

## 📌 과제 개요

- **과목명**: 데이터베이스시스템
- **학기**: 2025년도 1학기
- **담당 교수**: [교수님 성함]
- **제출자**: 정다연
- **기술 스택**: Java, JDBC, MySQL
- **구현 파일 저장 형식**: 순차적 텍스트 파일 기반 저장 + MySQL 동기화

---

## 🧩 프로젝트 구성

### 📁 주요 파일

| 파일명 | 설명 |
|--------|------|
| `Main.java` | 사용자 CLI 입력 처리, 메뉴 선택에 따라 기능 분기 |
| `DatabaseManager.java` | 레코드 입력/조회 기능, JDBC + 파일 I/O 통합 처리 |
| `JoinProcessor.java` | Merge Join 구현 (파일 기반), MySQL JOIN 결과와 비교 출력 |
| `TableCreator.java` | JDBC 기반 테이블 생성 기능 (수동 SQL 입력 기반) |
| `[table_name].txt` | 레코드가 저장되는 디스크 기반 파일 (Null Bitmap 포함) |

---

## 🛠️ 주요 기능 설명

### 1. 테이블 생성 (`CREATE TABLE`)
- JDBC를 활용하여 사용자의 SQL 입력으로 테이블 생성

### 2. 레코드 삽입 (`INSERT`)
- 사용자 입력을 받아, 다음 두 위치에 레코드를 동기화함:
  - ✅ MySQL DB (PreparedStatement 사용, NULL 처리 지원)
  - ✅ 디스크 텍스트 파일 (`[table].txt`)
    - 레코드는 다음 형식으로 저장됨:
      ```
      [null-bitmap(8)] + [값들 고정길이] + [다음 오프셋(8)] + '\n'
      ```
    - 블록당 3개 레코드 저장
    - 헤더 오프셋은 파일 시작 8바이트에 위치

### 3. 레코드 검색 (`SELECT`)
- 사용자로부터 첫 번째 속성의 범위 입력 (예: 100 ~ 200)
- 디스크 파일에서 해당 범위에 해당하는 레코드를 순차 탐색 후 출력

### 4. 조인 기능 (`JOIN`)
- Merge Join 알고리즘 구현:
  - 두 테이블을 첫 번째 속성 기준 정렬된 상태로 전제하고 조인 수행
  - 디스크 파일 기반 레코드를 로딩해 조인 결과 출력
- MySQL과 동일 조건으로 JOIN 수행한 결과도 출력

---

## 📂 실행 예시

### ▶ 실행 흐름

```text
Select a command you want to execute.
(1.CREATE TABLE  2.INSERT  3.SELECT  4.EXIT  5.JOIN)
>> 2
Enter a table for the record to be inserted.
>> student
Enter the number of records to insert: 1
Enter the values of the records in order, separated by semicolons.
|SID     |Name      |Major     |
>> 20230001;Kim Dayeon;CS
✅ MySQL Insert succeeded for Record 1
````

---

## 💡 설계 특징

* 🔄 **MySQL과 동기화**: 레코드 입력 시 DB + 파일 모두 저장
* 🧱 **레코드 저장 형식**:

  * NULL 여부 판단을 위해 Null Bitmap(8자리) 사용
  * 오프셋 정보를 활용한 연결형 구조
* 📚 **정렬 기반 Merge Join 구현**:

  * 중복 키 지원 (Block Nested Loop 형태 아님)
  * 파일 기반 조인 결과와 SQL 결과 비교로 정합성 확인 가능

---

## 🧪 테스트 조건 및 방법

* 테스트용 테이블은 `metadata` DB에서 수동 생성 또는 `TableCreator`로 작성
* 각 테이블당 3\~6개의 레코드를 입력 후, SELECT / JOIN 기능 실행

---

## 🧾 참고사항 및 한계

* 테이블은 사전 생성되어 있어야 하며, 첫 번째 속성이 정렬되어 있다고 가정
* 입력값이 속성 길이를 초과할 경우 예외 처리 후 skip
* UTF-8 한글은 고정 길이 저장 시 문자열이 잘릴 수 있음 → 영어 사용 권장

---

## 📌 실행 환경

* Java 17+
* MySQL 8.0+
* JDBC 드라이버: `com.mysql.cj.jdbc.Driver`
* OS: Windows 기준 디스크 경로 사용 (`\\` 구분자)

---

## 🙋 느낀점

이번 과제를 통해 메타데이터 기반 동적 SQL 생성, JDBC의 유연한 쿼리 구성, 디스크 파일 구조의 정형화 설계 및 조인 알고리즘에 대한 실습 경험을 쌓을 수 있었습니다. 단순한 SQL 실행을 넘어서 실제 스토리지와의 연계 및 범위 질의, 조인 연산의 구현은 DB 내부 구조에 대한 이해를 돕는 매우 유의미한 과정이었습니다.

---

## 📎 기타

* JDBC 연결 정보는 `DatabaseManager.java` 상단 상수로 구성되어 있음
* 테스트용 테이블 생성 SQL 예시는 `schema.sql` 등 별도 제공 가능
