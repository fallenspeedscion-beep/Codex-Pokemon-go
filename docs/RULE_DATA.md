# Importing advisor data

Choose **Settings → Import event rules (JSON)**. One import replaces the active shared reference set. Each evaluation uses the selected profile's records. Dates are ISO 8601 instants with UTC offset or Z. Windows include the start and exclude the end. Species and form comparisons ignore case. Use an asterisk for form only if the rule actually includes all forms. Use an explicit form such as Normal if form confirmation matters.

The following synthetic file illustrates the format. Its fictional species, move and historical window are test inputs.

```json
{
  "version": "fixture-1",
  "source": "Synthetic documentation example",
  "rules": [
    {
      "category": "Community Day",
      "species": "Example Basic",
      "form": "Normal",
      "start": "2000-01-01T00:00:00Z",
      "end": "2000-01-02T00:00:00Z",
      "evolvesTo": "Example Final",
      "move": "Example Wind",
      "slot": "Fast",
      "priority": "OPTIONAL",
      "basis": "Synthetic direct-evolution rule"
    },
    {
      "category": "Frustration",
      "species": "Example Final",
      "form": "*",
      "start": "2000-01-01T00:00:00Z",
      "end": "2000-01-02T00:00:00Z",
      "priority": "RECOMMENDED",
      "basis": "Synthetic species usefulness reference"
    }
  ],
  "battle": [
    {
      "species": "Example Final",
      "form": "Normal",
      "pve": "Situational",
      "basis": "Synthetic performance classification",
      "pvp": {"league":"Example league","cap":1500,"rank":123,"attack":0,"defense":14,"stamina":15}
    }
  ]
}
```

Allowed priorities are MUST DO, RECOMMENDED, OPTIONAL, SKIP and UNKNOWN. Community Day rules describe a species that can acquire the move by evolving into the named target during that window. Supply separate rows for eligible source species/forms. Complex evolution prerequisites must be checked by the player. Frustration blocks a Charged evolution move. A Fast evolution move uses a separate slot. Overlapping matching rows produce UNKNOWN.

Optional battle entries describe a particular species and form. The record editor displays the supplied PvE classification and basis. A supplied PvP IV rank requires an exact spread and recorded CP at or below the cap. Data providers must specify relevant level and resource assumptions in the basis. No rankings are derived from IV percentage.
