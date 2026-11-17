# MobileAim 3D 모델

MobileAim 거치대, 레이저 보어사이터(총구에 끼워 총열 방향으로 레이저 점을 쏘는 기구)의 3D 모델이다. STEP 은 설계 원본이고, STL·3mf 는 바로 출력하는 파일이다. 아직 출력하지 않은 설계안(`phone-clamp/`, `picatinny-rail-mount/`)과 그림을 만드는 스크립트(`rendering/`)도 함께 둔다.

## 출력 설정

- 프린터: Bambu Lab X2D
- 재료: PLA Tough+
- 벽 4겹, 채움 40%, 트리 서포트

## `phone-case/`: 케이스형 스마트폰 거치대

Galaxy S25 Ultra 전용이다. 2026-10-19 K2C1 25m 실사격 95발에 썼다.

- `case.step`·`case.stl`: 스마트폰을 넣는 케이스. T자 기둥을 피카티니 레일 마운트에 끼운다.
- `cap.step`: 케이스를 덮는 캡. 카메라 창이 있다.
- 나사 구멍: 케이스 6개는 Ø2.9 막힌 구멍이다(Ø2.8 에서 키움). 캡은 Ø3.3 관통(M3)이다.
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
- `laser-holder - airsoft.step`·`laser-holder - airsoft.stl`: 아카데미과학 K2C1 에어소프트건(총구 약 Ø22)용 홀더
  - 뒤쪽 소켓은 Ø22.5 원통이다. K2C1 판의 소염기 안으로 들어가는 원뿔은 없다.
  - 옆 받침 두 곳에 M5 인서트 너트(바깥지름 약 7.1mm, 길이 10mm 이하)를 인두로 박는다. 구멍은 Ø6.4, 깊이 10.5mm 다.
  - M5×25 노브 나사(B형)를 인서트에 돌려 넣어 총구를 누른다. 총구에 닿을 때 노브 머리는 받침 면에서 약 4mm 떠 있다.
  - 옆 받침이 공중에 뜨므로 트리 서포트를 켜고 출력한다.
  - `laser-holder-airsoft-switch - X2D.gcode.3mf`: 이 홀더와 회전 스위치를 한 판에 올린 BambuStudio 슬라이스(X2D, PLA Tough+, 0.20mm, 벽 4겹, 채움 40%, 트리 서포트, 50.6g, 약 1시간 41분)
  - `airsoft-version.py`: 출력한 K2C1 홀더 형상에서 이 형상을 만드는 FreeCAD 스크립트

## `laser-switch/`: 레이저 포인터 회전 스위치

Ø23 레이저 포인터에 끼우는 링이다. 링을 돌려 포인터의 누름 버튼을 눌러 둔다. 그래서 버튼을 계속 누르지 않아도 레이저가 켜져 있다.

- `laser-switch.step`·`laser-switch.stl`: 바깥 Ø35, 안쪽 Ø23.2, 높이 14mm
- `laser-switch.py`: FreeCAD 로 형상을 만드는 스크립트(`freecadcmd laser-switch.py`)
- 쓰는 법
  - 링 아래 면의 홈에 포인터 버튼을 맞추고 링을 밀어 끼운다. 윗면의 선이 홈 자리다.
  - 링을 약 75° 돌린다. 홈 안 경사면이 버튼을 눌러 레이저가 켜진다. 윗면의 점이 켜지는 자리다.
  - 거꾸로 돌리면 버튼이 홈으로 돌아와 꺼진다.
- 출력하지 않은 형상이다. 버튼을 누르는 깊이는 포인터마다 달라 출력해 맞춰 봐야 한다.

## `phone-clamp/`: 클램프형 스마트폰 거치대(설계안)

높이·폭·두께 조로 여러 기종을 잡는 거치대다. 아직 출력하지 않았다.

- `frame.step`: 몸체
- `height-jaw.step`·`width-jaw.step`·`thickness-jaw.step`: 세 방향 조
- `*-cap.step`: 각 조의 캡

## `picatinny-rail-mount/`: 피카티니 레일 마운트(설계안)

- `rail.step`: 135mm 피카티니 레일
- `clamp-left.step`·`clamp-right.step`: 좌우 클램프 블록

## `rendering/`, `renders/`: 그림

`rendering/` 의 스크립트가 `renders/` 에 그림을 그린다.

- `step2stl.py`: 이 폴더의 STEP 을 STL 로 바꾼다(`freecadcmd step2stl.py`). 다른 스크립트보다 먼저 돌린다.
  - STL 은 환경변수 `MOBILEAIM_RENDER_DIR` 의 폴더에 쓴다. 기본값은 `/tmp/mobileaim-render` 다. 다른 스크립트도 같은 폴더에서 읽는다.
- `k2c1-mount.py`: 2분할 케이스의 기둥을 K2C1 의 Olight 레일 마운트에 바로 끼운 모습(렌즈가 총열 위쪽인 배치, 총구에 레이저 홀더)
- `parts.py`: 출력 부품 4개(case·cap·laser-holder·knob)를 같은 각도·색·크기의 정사각 그림으로 그린다
- `phone-clamp.py`: 클램프에 폰을 끼운 모습, 단면, K2C1 장착 제안(레이저 홀더 포함)
- `laser-holder.py`: 레이저 홀더·노브·포인터·회전 스위치
- `shared.py`: 위 스크립트가 함께 쓰는 색과 형상
- 그림은 2026-10-19 실사격 때 출력한 `laser-holder/laser-holder - printed.step` 형상을 쓴다.
- 폰·K2C1·레이저 포인터 스위치 형상(`reference/`)은 남의 것이라 이 repo 에 없다.
  - `galaxy-s25-ultra.step`: Galaxy S25 Ultra 형상
  - `daewoo-k2c1.obj`: K2C1 소총. 장착 어림용 근사 모델이다.
  - `laser-pointer-switch/`: Ø23 레이저 포인터용 회전 스위치(riodoro1, Thingiverse 2557550, CC BY-SA). 출력 부품은 `laser-switch/` 의 자체 설계로 바꿨다. 그래서 그 형상을 쓰는 그림은 이 repo 만으로는 다시 그릴 수 없다.

## 원본 설계

- 원본은 Onshape 문서다: https://cad.onshape.com/documents/92d466bd486523eb8996a316/w/ca53ada99d471904a5b87cf1/e/5cb351b70f95595dda9b2a19
- Onshape 원본은 직접 고치지 못한다. STEP 을 FreeCAD(`freecadcmd`)로 읽어 고친다.
- Onshape 문서는 공개라 `/api/v6/.../features`·elements·shadedviews(그림)는 인증 없이 읽힌다. 질량·STL·스케치 치수는 401 이다.
