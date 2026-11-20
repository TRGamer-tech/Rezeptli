# ADR 0010: Der Kochtimer klingelt – auch wenn die App nicht zu sehen ist

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Der Timer im Kochmodus zählte herunter und zeigte am Ende "Zeit ist um" – stumm. Wer nicht
gerade auf das Telefon sah, merkte nichts. Dazu liess sich die Zeit nicht anpassen: Stand im
Rezept "20 Minuten", waren es 20 Minuten, auch wenn der eigene Ofen 23 braucht.

Die erste Fassung hatte den Ton bewusst weggelassen, um ohne weitere Berechtigung
auszukommen. Das war die falsche Abwägung: Ein Timer, den man überhört, ist keiner.

## Entscheidung

### Der Timer gehört der App, nicht dem Bildschirm

Bisher lief er im ViewModel des Kochmodus und zählte Sekunden. Jetzt gibt es *einen* Timer
für die ganze App (`CookingTimer`), der sich den **Zeitpunkt** merkt, an dem die Zeit um
ist, und die Restzeit daraus errechnet. Gemessen wird mit der Uhr seit dem Einschalten des
Geräts: Sie läuft im Schlaf weiter und springt nicht, wenn die Wanduhr sich mit dem Netz
abgleicht. Verschläft das Telefon einen Takt, stimmt die Anzeige danach trotzdem – ein
Zähler wäre nachgegangen.

Eine Minute mehr oder weniger verschiebt bloss diesen Zeitpunkt. Vor dem Start lässt sich
der Vorschlag aus dem Rezept verstellen, während des Laufs die Restzeit; nach dem Klingeln
heisst "noch eine Minute" genau das.

### Ein Dienst im Vordergrund statt eines exakten Weckers

Damit der Timer klingelt, wenn das Telefon gesperrt oder eine andere App offen ist, gibt es
unter Android zwei Wege:

- **Exakter Wecker** (`AlarmManager`). Braucht seit Android 12 eine eigene Berechtigung. Die
  eine Fassung (`SCHEDULE_EXACT_ALARM`) ist seit Android 14 ab Werk verweigert – die Person
  müsste dafür in die Systemeinstellungen. Die andere (`USE_EXACT_ALARM`) ist Wecker- und
  Kalender-Apps vorbehalten; eine Rezept-App fiele damit bei einer Store-Prüfung durch.
- **Dienst im Vordergrund.** Solange ein Timer läuft, hält ein Dienst mit sichtbarer
  Mitteilung die App am Leben und das Gerät wach. Er darf gestartet werden, weil ein Timer
  immer von Hand gestellt wird – die App ist in dem Moment zu sehen.

Gewählt ist der Dienst (`CookingTimerService`, Art `specialUse`). Er rechnet nichts selbst,
sondern liest denselben Timer wie der Bildschirm. Seine Mitteilung zählt die Restzeit
herunter und bietet "Stoppen" – auch vom Sperrbildschirm aus.

Das Wachhalten hat ein Ablaufdatum (Restzeit plus Klingeldauer). Es kann also nicht hängen
bleiben, was auch geschieht.

### Geklingelt wird mit dem Weckton des Telefons

`AlarmRinger` spielt den Ton, den die Person in den Einstellungen ihres Telefons als
Weckton gewählt hat – als *Wecker*, nicht als Musik. Damit gilt die Wecker-Lautstärke, und
der Ton ist auch zu hören, wenn das Telefon auf lautlos steht; so hält es auch die Uhr-App.
Läuft nebenher Musik oder ein Podcast, wird sie für die Dauer leiser. Dazu vibriert das
Telefon.

Hat das Telefon keinen Weckton oder lässt er sich nicht abspielen, springt ein
mitgelieferter ein (`res/raw/timer_klingeln.ogg`, drei Glockenschläge, für die App
synthetisch erzeugt – es gibt keine fremden Rechte daran). Stumm bleibt der Timer nie.

Das Klingeln hängt bewusst **nicht** am Dienst: Verweigert Android dessen Start, klingelt
der Timer trotzdem, solange die App lebt. Nach fünf Minuten ohne Reaktion verstummt er von
selbst; die Mitteilung bleibt stehen.

### Der Timer endet mit dem Kochmodus – aber nicht stillschweigend

Ein Timer, den man nirgends mehr sieht, wäre eine Falle. Deshalb gehört er zu einem
Kochvorgang: Wer den Kochmodus schliesst, während die Zeit läuft, wird gefragt. Innerhalb
des Kochmodus bleibt er beim Blättern sichtbar – als Streifen über den Knöpfen, der zum
Schritt zurückführt und das Klingeln abstellen lässt.

## Folgen

- Neue Berechtigungen im Manifest: Mitteilungen, Vibration, Wachhalten, Dienst im
  Vordergrund. Benutzt werden sie nur, solange ein Timer läuft. Um die Mitteilungen bittet
  die App beim Start des ersten Timers; lehnt die Person ab, klingelt der Timer trotzdem –
  nur zeigt Android dann keine Restzeit.
- Für eine Veröffentlichung im Play Store verlangt die Art `specialUse` eine Begründung im
  Formular. Sie steht schon im Manifest.
- Es gibt einen Timer aufs Mal. Ein zweiter ersetzt den ersten.
- Ob wirklich ein Ton läuft und ob Android den Dienst annimmt, lässt sich nur auf einem
  Gerät prüfen. Zwei Gerätetests tun das auf dem Emulator der CI: `AlarmRingerTest` und
  `CookingTimerServiceTest`. Die Rechenregeln prüfen gewöhnliche Tests (`CookingTimerTest`).
