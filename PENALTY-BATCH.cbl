       IDENTIFICATION DIVISION.
       PROGRAM-ID. PENALTY-BATCH.
       AUTHOR. LA-MAISON-INTERNS.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
      * We expect Ktor to create this file before running COBOL
           SELECT INPUT-FILE ASSIGN TO 'batch_input.txt'
               ORGANIZATION IS LINE SEQUENTIAL.
      * COBOL will create this file for Ktor to read afterwards
           SELECT OUTPUT-FILE ASSIGN TO 'batch_output.txt'
               ORGANIZATION IS LINE SEQUENTIAL.

       DATA DIVISION.
       FILE SECTION.
       FD  INPUT-FILE.
       01  INPUT-RECORD.
           05 IN-TENANT-ID      PIC X(28).
           *>Accept explicit +/- signs from Kotlin
           05  IN-BALANCE        PIC S9(8) SIGN IS LEADING SEPARATE.
           05 IN-RENT           PIC 9(9).

       FD  OUTPUT-FILE.
       01  OUTPUT-RECORD.
           05 OUT-TENANT-ID     PIC X(28).
           *>Output explicit +/- signs back to Kotlin
           05 OUT-NEW-BALANCE       PIC S9(8) SIGN IS LEADING SEPARATE.

       WORKING-STORAGE SECTION.
       01  WS-EOF-FLAG          PIC X VALUE 'N'.
       01  WS-LATE-FEE          PIC 9(9) VALUE 0.

       PROCEDURE DIVISION.
       0000-MAIN-LOGIC.
           OPEN INPUT INPUT-FILE
           OPEN OUTPUT OUTPUT-FILE

           PERFORM 1000-PROCESS-RECORD
              UNTIL WS-EOF-FLAG = 'Y'

           CLOSE INPUT-FILE
           CLOSE OUTPUT-FILE

           DISPLAY "BATCH PROCESSING COMPLETE."
           STOP RUN.

       1000-PROCESS-RECORD.
           READ INPUT-FILE
               AT END
                   MOVE 'Y' TO WS-EOF-FLAG
               NOT AT END
                   MOVE IN-TENANT-ID TO OUT-TENANT-ID

                   *> CASE 1: On-Time or Credit (Balance <= 0)
                   IF IN-BALANCE <= 0
                       COMPUTE OUT-NEW-BALANCE = IN-BALANCE + IN-RENT

                   *> CASE 2: Late Payment (Balance > 0)
                   ELSE
                       COMPUTE WS-LATE-FEE = IN-RENT * 0.05
                       COMPUTE OUT-NEW-BALANCE =
                               IN-BALANCE + IN-RENT + WS-LATE-FEE
                   END-IF

                   WRITE OUTPUT-RECORD
           END-READ.
