package com.roguechests;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class RogueChestsOverlay extends Overlay
{
	private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 180);
	private static final Color TITLE_COLOR = new Color(220, 60, 60);
	private static final Color NAME_COLOR = Color.WHITE;

	private final RogueChestsPlugin plugin;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	private RogueChestsOverlay(RogueChestsPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		panelComponent.setBackgroundColor(BACKGROUND_COLOR);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Only show when in the Rogue Chests FC and there are flagged members
		Set<String> lowLevelMembers = plugin.getLowLevelMembers();
		if (lowLevelMembers.isEmpty() || !plugin.isInRogueChestsFc())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Low Thieving (<84)")
			.color(TITLE_COLOR)
			.build());

		// Sort alphabetically for consistent ordering
		List<String> sorted = new ArrayList<>(lowLevelMembers);
		sorted.sort(String::compareToIgnoreCase);

		for (String name : sorted)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(name)
				.leftColor(NAME_COLOR)
				.build());
		}

		return panelComponent.render(graphics);
	}
}
