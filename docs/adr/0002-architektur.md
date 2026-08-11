# ADR 0002: Dreischichtige Architektur mit MVVM

**Status:** Angenommen · **Datum:** 2026-08-10

## Kontext

Die App soll wartbar bleiben und ihre Kernlogik – der Zutaten-Parser und die
Swipe-Auswertung – muss ohne Emulator schnell testbar sein.

## Entscheidung

Drei Schichten:

- **`domain/`** – Modelle, Use Cases, Parser, Repository-Interfaces. Reines Kotlin.
- **`data/`** – Room-Entities, DAOs, Mapper, Repository-Implementierungen.
- **`presentation/`** – Compose-Screens und ViewModels nach MVVM mit unidirektionalem
  Datenfluss: ein `UiState` je Screen als `StateFlow`, einmalige Ereignisse über einen
  `Channel`.

Abhängigkeiten zeigen immer nach innen: `presentation → domain ← data`.

### Ausnahme: `androidx.paging` in der Domain-Schicht

`RecipeRepository.pagedSummaries()` gibt `Flow<PagingData<RecipeSummary>>` zurück und
bringt damit `paging-common` in die Domain-Schicht. Das ist die einzige bewusste Ausnahme
von der Regel "Domain ohne Framework-Abhängigkeiten":

- `paging-common` ist plattformunabhängig und enthält keinen Android-Framework-Code; die
  Domain-Schicht bleibt auf einer normalen JVM testbar.
- Die Alternative – eine eigene Paging-Abstraktion in der Domain und ein Adapter in der
  Data-Schicht – wäre deutlich mehr Code für keinen praktischen Gewinn.

Die eigentliche Kernlogik (`domain/parser/`, `domain/swipe/`, `domain/model/`) bleibt
komplett frei von externen Abhängigkeiten.

## Konsequenzen

- Parser und Matcher laufen als schnelle JVM-Tests, ohne Emulator und ohne Robolectric.
- ViewModels kennen keine Room-Entities; das Mapping passiert in `data/mapper/`.
- Für jeden Screen gibt es eine `UiState`-Datenklasse – etwas mehr Boilerplate, dafür ist
  jeder Zustand explizit und im Test rekonstruierbar.
