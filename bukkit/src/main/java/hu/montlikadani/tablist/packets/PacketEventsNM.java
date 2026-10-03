package hu.montlikadani.tablist.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import hu.montlikadani.api.IPacketNM;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Packet layer for Minecraft versions without a dedicated NMS module (26.x and newer), built on the PacketEvents
 * plugin instead of server internals. Objects handed around by TabList stay opaque:
 * components are Adventure {@link Component}s, "entity players" are {@link Entry} (fake) or {@link Player} (real).
 */
public final class PacketEventsNM implements IPacketNM {

	/** A fake tab-list entry (what the NMS modules model as a dummy EntityPlayer). */
	public static final class Entry {

		final UserProfile profile;
		Component listName;
		int latency;
		GameMode gameMode = GameMode.SURVIVAL;

		Entry(UserProfile profile) {
			this.profile = profile;
		}
	}

	private final Map<String, Object[]> objectives = new HashMap<>(); // name -> {Component display, ScoreFormat, RenderType}

	public PacketEventsNM() {
		if (PacketEvents.getAPI() == null) {
			throw new IllegalStateException("PacketEvents API is not available");
		}
	}

	@Override
	public void sendPacket(Player player, Object packet) {
		if (packet instanceof PacketWrapper<?>) {
			PacketEvents.getAPI().getPlayerManager().sendPacket(player, (PacketWrapper<?>) packet);
		}
	}

	@Override
	public void addPlayerChannelListener(Player player, List<Class<?>> classesToListen) {
		// The NMS modules use this to rewrite spectator entries / team tags in outgoing packets.
		// Not available without NMS; the plugin works without it (those two extras are skipped).
	}

	@Override
	public void removePlayerChannelListener(Player player) {
	}

	@Override
	public Object getPlayerHandle(Player player) {
		return player;
	}

	@Override
	public Object fromJson(String json) {
		return GsonComponentSerializer.gson().deserialize(json);
	}

	@Override
	public void sendTabTitle(Player player, Object header, Object footer) {
		sendPacket(player, new WrapperPlayServerPlayerListHeaderAndFooter((Component) header, (Component) footer));
	}

	@Override
	public Object getNewEntityPlayer(com.mojang.authlib.GameProfile profile) {
		List<TextureProperty> textures = new ArrayList<>();

		for (com.mojang.authlib.properties.Property property : profile.properties().values()) {
			textures.add(new TextureProperty(property.name(), property.value(), property.signature()));
		}

		return new Entry(new UserProfile(profile.id(), profile.name(), textures));
	}

	private static GameMode mode(Player player) {
		switch (player.getGameMode()) {
			case CREATIVE:
				return GameMode.CREATIVE;
			case ADVENTURE:
				return GameMode.ADVENTURE;
			case SPECTATOR:
				return GameMode.SPECTATOR;
			default:
				return GameMode.SURVIVAL;
		}
	}

	private static WrapperPlayServerPlayerInfoUpdate.PlayerInfo info(Object entityPlayer) {
		if (entityPlayer instanceof Entry) {
			Entry e = (Entry) entityPlayer;
			return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(e.profile, true, e.latency, e.gameMode, e.listName, null);
		}

		Player p = (Player) entityPlayer;
		UserProfile profile = PacketEvents.getAPI().getPlayerManager().getUser(p).getProfile();
		return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile, true, p.getPing(), mode(p),
				p.playerListName(), null);
	}

	@Override
	public Object updateDisplayNamePacket(Object entityPlayer, Object component, boolean listName) {
		if (listName) {
			setListName(entityPlayer, component);
		}

		WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = info(entityPlayer);
		info.setDisplayName((Component) component);
		return new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME, info);
	}

	@Override
	public void setListName(Object entityPlayer, Object component) {
		if (entityPlayer instanceof Entry) {
			((Entry) entityPlayer).listName = (Component) component;
		} else {
			((Player) entityPlayer).playerListName((Component) component);
		}
	}

	@Override
	public Object newPlayerInfoUpdatePacketAdd(Object... entityPlayer) {
		List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> list = new ArrayList<>(entityPlayer.length);

		for (Object one : entityPlayer) {
			list.add(info(one));
		}

		return new WrapperPlayServerPlayerInfoUpdate(EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
				WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
				WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME), list);
	}

	@Override
	public Object updateLatency(Object entityPlayer) {
		return new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY, info(entityPlayer));
	}

	@Override
	public Object removeEntityPlayers(Object... entityPlayers) {
		List<UUID> ids = new ArrayList<>(entityPlayers.length);

		for (Object one : entityPlayers) {
			ids.add(one instanceof Entry ? ((Entry) one).profile.getUUID() : ((Player) one).getUniqueId());
		}

		return new WrapperPlayServerPlayerInfoRemove(ids);
	}

	@Override
	public void setInfoData(Object info, UUID id, int ping, Object component) {
		WrapperPlayServerPlayerInfoUpdate update = (WrapperPlayServerPlayerInfoUpdate) info;

		for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo : update.getEntries()) {
			if (playerInfo.getProfileId().equals(id)) {
				if (ping != -2) {
					playerInfo.setLatency(ping);
				}

				playerInfo.setDisplayName((Component) component);
				break;
			}
		}
	}

	private static WrapperPlayServerTeams.NameTagVisibility visibilityOf(Player player) {
		WrapperPlayServerTeams.NameTagVisibility visibility = WrapperPlayServerTeams.NameTagVisibility.ALWAYS;

		for (Team team : player.getScoreboard().getTeams()) {
			switch (team.getOption(Team.Option.NAME_TAG_VISIBILITY)) {
				case FOR_OTHER_TEAMS:
					visibility = WrapperPlayServerTeams.NameTagVisibility.HIDE_FOR_OTHER_TEAMS;
					break;
				case FOR_OWN_TEAM:
					visibility = WrapperPlayServerTeams.NameTagVisibility.HIDE_FOR_OWN_TEAM;
					break;
				case NEVER:
					visibility = WrapperPlayServerTeams.NameTagVisibility.NEVER;
					break;
				default:
					break;
			}
		}

		return visibility;
	}

	@Override
	public void createBoardTeam(String teamName, Player player, boolean followNameTagVisibility) {
		WrapperPlayServerTeams.NameTagVisibility visibility = followNameTagVisibility ? visibilityOf(player)
				: WrapperPlayServerTeams.NameTagVisibility.ALWAYS;

		WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(Component.empty(),
				null, null, visibility, WrapperPlayServerTeams.CollisionRule.ALWAYS, NamedTextColor.WHITE,
				WrapperPlayServerTeams.OptionData.NONE);

		for (Player one : Bukkit.getOnlinePlayers()) {
			sendPacket(one, new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo,
					Collections.singletonList(player.getName())));
		}
	}

	@Override
	public Object unregisterBoardTeamPacket(String teamName) {
		return new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE,
				(WrapperPlayServerTeams.ScoreBoardTeamInfo) null, Collections.<String>emptyList());
	}

	@Override
	public Object createObjectivePacket(String objectiveName, Object nameComponent, ObjectiveFormat objectiveFormat,
										Object formatComponent) {
		ScoreFormat format = null;

		if (objectiveFormat != null) {
			switch (objectiveFormat) {
				case FIXED:
					format = ScoreFormat.fixedScore((Component) formatComponent);
					break;
				case STYLED:
					String[] arr = (String[]) formatComponent;
					Style style = Style.empty();

					for (String name : arr) {
						NamedTextColor color = NamedTextColor.NAMES.value(name.toLowerCase(java.util.Locale.ROOT));

						if (color != null) {
							style = style.color(color);
						} else {
							net.kyori.adventure.text.format.TextDecoration deco = net.kyori.adventure.text.format.TextDecoration.NAMES
									.value(name.toLowerCase(java.util.Locale.ROOT));

							if (deco != null) {
								style = style.decoration(deco, true);
							}
						}
					}

					format = ScoreFormat.styledScore(style);
					break;
				default:
					break;
			}
		}

		objectives.putIfAbsent(objectiveName, new Object[] { nameComponent, format,
				WrapperPlayServerScoreboardObjective.RenderType.INTEGER });
		return new Obj(objectiveName);
	}

	/** Handle that identifies an objective by name (TabList passes it back into scoreboard*Packet). */
	private static final class Obj {

		final String name;

		Obj(String name) {
			this.name = name;
		}
	}

	@Override
	public Object scoreboardObjectivePacket(Object objective, int mode) {
		Obj obj = (Obj) objective;
		Object[] data = objectives.get(obj.name);
		WrapperPlayServerScoreboardObjective.ObjectiveMode m = mode == 1 ? WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE
				: mode == 2 ? WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE
				: WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE;

		return new WrapperPlayServerScoreboardObjective(obj.name, m, (Component) data[0],
				(WrapperPlayServerScoreboardObjective.RenderType) data[2], (ScoreFormat) data[1]);
	}

	@Override
	public Object scoreboardDisplayObjectivePacket(Object objective, int slot) {
		return new WrapperPlayServerDisplayScoreboard(slot, ((Obj) objective).name);
	}

	@Override
	public Object changeScoreboardScorePacket(String objectiveName, String scoreName, int score) {
		Object[] data = objectives.get(objectiveName);
		return new WrapperPlayServerUpdateScore(scoreName, WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
				objectiveName, score, Component.empty(), data == null ? null : (ScoreFormat) data[1]);
	}

	@Override
	public Object removeScoreboardScorePacket(String objectiveName, String scoreName, int score) {
		objectives.remove(objectiveName);
		return new WrapperPlayServerResetScore(scoreName, objectiveName);
	}

	@Override
	public Object createScoreboardHealthObjectivePacket(String objectiveName, Object nameComponent) {
		objectives.put(objectiveName, new Object[] { nameComponent, null, WrapperPlayServerScoreboardObjective.RenderType.HEARTS });
		return new Obj(objectiveName);
	}

	@Override
	public double[] serverTps() {
		return Bukkit.getServer().getTPS();
	}

	@Override
	public int playerPing(Player player) {
		return player.getPing();
	}
}
