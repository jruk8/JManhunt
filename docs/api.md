# API

JManhunt exposes a small public API so other plugins can read match state and
react to match lifecycle changes. It is the integration point used by
[**JManhunt-Challenges**](https://github.com/jruk8/JManhunt-Challenges) — the
companion plugin that provides the built-in challenges (no-jump, one-heart and
lucky-blocks).

## Getting the API

The API is registered with Bukkit's `ServicesManager` when JManhunt enables:

```java
import com.jruk8.jmanhunt.api.JManhuntApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

RegisteredServiceProvider<JManhuntApi> provider =
        Bukkit.getServicesManager().getRegistration(JManhuntApi.class);
JManhuntApi api = provider == null ? null : provider.getProvider();
```

Add `JManhunt` as a dependency to your plugin:

```yaml
depend:
  - JManhunt
```

### Build integration

JManhunt publishes its API under the JitPack coordinate
`com.github.jruk8:JManhunt:<tag>`. For local development, publish to your
local Maven repository from the JManhunt checkout:

```shell
./gradlew publishToMavenLocal
```

and add `mavenLocal()` to your repositories.

## API surface

```java
public interface JManhuntApi {

    boolean isMatchActive();        // a match exists (including the pre-start window)
    boolean hasGameBegun();         // the match has begun (pre-start window over)
    boolean isMatchEnding();        // the end delay is running
    long    getMatchId();           // incrementing id of the current/last match
    PlayerRole getRole(UUID playerId);
    boolean isParticipant(UUID playerId);
}
```

### `PlayerRole`

`PlayerRole` is the public role enum:

| Value | Meaning |
| --- | --- |
| `NONE` | Not assigned to the match. |
| `HUNTER` | An active hunter. |
| `SPEEDRUNNER` | An active speedrunner. |
| `AFK` | Marked as away/AFK. |

`PlayerRole#isParticipant()` returns `true` for `HUNTER` and `SPEEDRUNNER`.

## Events

All events extend `org.bukkit.event.Event` and are fired synchronously. Events
are in the package `com.jruk8.jmanhunt.api.events`.

| Event | When it fires | Data |
| --- | --- | --- |
| `JMatchStartEvent` | A match is created and announced (pre-start window begins). | `getMatchId()` |
| `JGameBeginEvent` | The game actually begins (after the pre-start window). | `getMatchId()` |
| `JMatchEndEvent` | A winner is announced (end delay still running). | `getMatchId()`, `getWinner()` |

```java
import com.jruk8.jmanhunt.api.events.JGameBeginEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class MyListener implements Listener {

    @EventHandler
    public void onGameBegin(JGameBeginEvent event) {
        Long matchId = event.getMatchId();
        // e.g. apply one-heart here for the active match
    }
}
```

## Example: applying one-heart at game begin

```java
import com.jruk8.jmanhunt.api.JManhuntApi;
import com.jruk8.jmanhunt.api.PlayerRole;
import com.jruk8.jmanhunt.api.events.JGameBeginEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class OneHeartChallenge implements Listener {

    private final JManhuntApi api;

    public OneHeartChallenge(JManhuntApi api) {
        this.api = api;
    }

    @EventHandler
    public void onGameBegin(JGameBeginEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (api.isParticipant(player.getUniqueId())) {
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)
                        .setBaseValue(2.0);
                player.setHealth(2.0);
            }
        }
    }
}
```

## Versioning

The API follows the plugin's versioning (SemVer via the existing
axion-release setup). Breaking changes to `com.jruk8.jmanhunt.api` bump the
plugin's major or minor version and are described in the
[CHANGELOG](https://github.com/jruk8/JManhunt/blob/main/CHANGELOG.md).