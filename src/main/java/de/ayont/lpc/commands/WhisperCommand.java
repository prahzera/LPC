package de.ayont.lpc.commands;

import de.ayont.lpc.LPC;
import de.ayont.lpc.moderation.ModResult;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.status.PlayerStatus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WhisperCommand implements TabExecutor, Listener {

    private static final MiniMessage FORMAT = MiniMessage.miniMessage();
    private static final String PREFIX = "<red><bold>Hapore</bold></red> <dark_gray>·</dark_gray> "
            + "<white>Susurro</white> <gray>»</gray> ";
    private final LPC plugin;
    private final Map<UUID, UUID> lastContact = new ConcurrentHashMap<>();

    public WhisperCommand(LPC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            notice(sender, "Este comando se usa dentro del juego.");
            return true;
        }

        boolean reply = command.getName().equals("reply");
        int firstWord = reply ? 0 : 1;
        if (args.length <= firstWord) {
            notice(player, reply ? "Usá /r <mensaje>." : "Usá /msg <jugador> <mensaje>.");
            return true;
        }

        Player target;
        if (reply) {
            UUID previous = lastContact.get(player.getUniqueId());
            target = previous == null ? null : plugin.getServer().getPlayer(previous);
        } else {
            target = plugin.getServer().getPlayerExact(args[0]);
        }
        if (target == null || !target.isOnline() || !player.canSee(target)) {
            notice(player, reply ? "Tu último contacto ya no está disponible." : "Ese jugador no está disponible.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            notice(player, "Elegí otro jugador para enviar un susurro.");
            return true;
        }

        String message = String.join(" ", Arrays.asList(args).subList(firstWord, args.length)).strip();
        if (message.isEmpty()) {
            notice(player, "Escribí un mensaje.");
            return true;
        }

        if (plugin.getServer().getPluginManager().isPluginEnabled("UniversalAdmin")
                && UniversalAdminProvider.find()
                        .flatMap(admin -> admin.services().get(PlayerStatus.class))
                        .flatMap(status -> status.chatMute(player.getUniqueId()))
                        .isPresent()) {
            notice(player, "Tenés el chat silenciado.");
            return true;
        }

        ModResult result = plugin.getModerationService().process(player, message);
        if (result.isBlocked()) {
            plugin.send(player, result.notice());
            return true;
        }
        String deliveredText = result.text() == null ? message : result.text();
        lastContact.put(player.getUniqueId(), target.getUniqueId());
        lastContact.put(target.getUniqueId(), player.getUniqueId());
        deliver(player, "Para", target.getName(), deliveredText);
        deliver(target, "De", player.getName(), deliveredText);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !command.getName().equals("msg") || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(target -> !target.equals(player) && player.canSee(target))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastContact.remove(event.getPlayer().getUniqueId());
    }

    private void notice(CommandSender sender, String message) {
        plugin.send(sender, FORMAT.deserialize(PREFIX + "<red><message></red>",
                Placeholder.unparsed("message", message)));
    }

    private void deliver(Player player, String direction, String name, String message) {
        plugin.send(player, FORMAT.deserialize(PREFIX + "<gray><direction></gray> "
                        + "<gold><name></gold><dark_gray>:</dark_gray> <white><message></white>",
                Placeholder.unparsed("direction", direction),
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("message", message)));
    }
}
