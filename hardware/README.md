# MobileAim 3D 출력 부품

MobileAim 거치대와 레이저 보어사이터(총구에 끼워 총열 방향으로 레이저 점을 쏘는 기구)를 출력하는 파일이다. STEP 은 설계 원본이고, STL·3mf 는 바로 출력하는 파일이다.

## 출력 설정

- 프린터: Bambu Lab X2D
- 재료: PLA Tough+
- 벽 4겹, 채움 40%, 트리 서포트

## `phone-case/`: 케이스형 스마트폰 거치대

Galaxy S25 Ultra 전용이다. 2026-10-19 K2C1 25m 실사격 95발에 썼다.

- `case.step`·`case.stl`: 스마트폰을 넣는 케이스. T자 기둥을 피카티니 레일 마운트에 끼운다.
- `cap.step`: 케이스를 덮는 캡. 카메라 창이 있다.
- `case-cap - X2D.gcode.3mf`: case·cap 한 판 BambuStudio 슬라이스(111.6g, 약 2시간 49분)
- 조립
  - 스마트폰을 case 에 넣고 cap 을 M3 나사 6개로 조인다.
  - case 의 T자 기둥을 피카티니 레일 마운트에 끼워 총 상부 레일에 고정한다(2026-10-19 실사격에는 Olight 레일 마운트를 썼다).

## `laser-holder/`: 레이저 보어사이터 홀더

K2C1 소염기(총구 끝 부품)에 끼워 영점 초기 정렬에 쓴다.

- `laser-holder.step`: 최종 형상. 뒤쪽 Ø24 소켓을 소염기에 끼우고, 앞쪽 Ø23.4 구멍에 Ø23 레이저 포인터를 끼운다.
- `knob.step`: 노브 나사 손잡이. M4×16 육각 볼트와 너트를 넣어 두 개를 만든다. 소켓 벽을 눌러 홀더를 소염기에 고정한다.
- `laser-holder - printed.step`: 2026-10-19 실사격 때 출력한 형상. K2C1 소염기에 맞지 않아 최종 형상으로 고쳤다.
- `knob-version.py`·`socket-knob-version.py`: FreeCAD 로 이전 형상에서 최종 형상을 만드는 스크립트
- 레이저 포인터의 회전 스위치는 [Thingiverse 2557550](https://www.thingiverse.com/thing:2557550)(CC BY-SA)을 출력해 쓴다.
