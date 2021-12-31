import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import javax.swing.JOptionPane;

import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;

public class NBTOptimizer {
	public static void main(String[] args) {
		try {
			if (args.length < 1) {
				FileDialog dialog = new FileDialog(new Frame());
				dialog.setMode(FileDialog.LOAD);
				dialog.setMultipleMode(false);
				dialog.setTitle("Select NBT file");
				dialog.setVisible(true);
				new NBTOptimizer(dialog.getFiles()[0]);
			} else {
				new NBTOptimizer(new File(args[0]));
			}
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e);
		}
		System.exit(0);
	}

	static int YLIMIT;
	static int MOVELIMIT;
	static int THRESHOLD;
	static int SECONDMOVELIMIT;
	static int SECONDTHRESHOLD;
	static int VALUERANGE;
	static boolean LOG = false;

	int X;
	int Y;
	int Z;
	int underblockid = -1;
	Pixel[][] pixelmap;
	ListTag<CompoundTag> blocks;
	ListTag<IntTag> size;
	NamedTag rawtag;

	LineManager[] managers;
	List<LineManager> sortinglist;
	HashSet<Integer> underneeds = new HashSet<Integer>();

	public NBTOptimizer(File file) throws IOException {

		loadProperties();
		load(file);
		construct();
		pixverify();
		int bd = difficulty();
		System.out.println("Difficulty: " + difficulty());

		/**
		 * Solving
		 */
		System.out.println("Optimizer Computing...");
		System.out.println("phase 1:");
		System.out.println(solve(0, MOVELIMIT, THRESHOLD) + " moves operated");
		fixconnectness();
		System.out.println("phase 2:");
		System.out.println(solve(-1, SECONDMOVELIMIT, SECONDTHRESHOLD) + " moves operated");
		fixconnectness();
		System.out.println("phase 3:");
		System.out.println(solve(1, SECONDMOVELIMIT, SECONDTHRESHOLD) + " moves operated");
		fixconnectness();
		System.out.println("phase 4:");
		System.out.println(solve(0, MOVELIMIT, THRESHOLD) + " moves operated");
		fixconnectness();
		System.out.println("phase 5:");
		System.out.println(solve(-1, SECONDMOVELIMIT, SECONDTHRESHOLD) + " moves operated");
		fixconnectness();
		System.out.println("phase 6:");
		System.out.println(solve(1, SECONDMOVELIMIT, SECONDTHRESHOLD) + " moves operated");
		fixconnectness();
		System.out.println("phase 7:");
		System.out.println(solve(0, MOVELIMIT, THRESHOLD) + " moves operated");
		fixconnectness();
		pixverify();
		editnbt();
		pixverify();
		int od = difficulty();
		System.out.println(
				String.format("Difficulty: %d -> %d (%s off)", bd, od, (int) ((1f - (float) od / bd) * 100) + "%"));

		write(file);

	}

	private void loadProperties() throws FileNotFoundException, IOException {
		Properties properties = new Properties();
		properties.load(new FileReader(new File("settings.properties")));
		YLIMIT = Integer.parseInt(properties.getProperty("Ylimit"));
		MOVELIMIT = Integer.parseInt(properties.getProperty("Warplimit"));
		THRESHOLD = Integer.parseInt(properties.getProperty("Threshold"));
		SECONDMOVELIMIT = Integer.parseInt(properties.getProperty("SecondWarplimit"));
		SECONDTHRESHOLD = Integer.parseInt(properties.getProperty("SecondThreshold"));
		VALUERANGE = Integer.parseInt(properties.getProperty("ValueRange"));
		LOG = Boolean.parseBoolean(properties.getProperty("Log"));
	}

	private void load(File file) throws IOException {
		rawtag = NBTUtil.read(file);
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		blocks = cpt.getListTag("blocks").asCompoundTagList();
		size = cpt.getListTag("size").asIntTagList();
		X = size.get(0).asInt();
		Y = size.get(1).asInt();
		Z = size.get(2).asInt();
		System.out.println(
				String.format("Loaded: %s  size:(%d, %d, %d)  blocks: %d", file.getName(), X, Y, Z, blocks.size()));
	}

	private String nameOf(int id) {
		String name = "unknown";
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		ListTag<CompoundTag> palette = cpt.getListTag("palette").asCompoundTagList();
		if (id < palette.size())
			name = palette.get(id).getString("Name");
		if (name == null)
			throw new Error("illegal palette");
		return name;
	}

	private void construct() {
		int[][] originalmap = new int[X][Z];
		int[][] originalid = new int[X][Z];

		pixelmap = new Pixel[X][Z];
		for (CompoundTag tag : blocks) {
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			IntTag idtag = tag.getIntTag("state");
			int x = pos.get(0).asInt();
			int y = pos.get(1).asInt();
			int z = pos.get(2).asInt();
			if (originalmap[x][z] < y) {
				originalmap[x][z] = y;
				originalid[x][z] = idtag.asInt();
			}
		}
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				pixelmap[x][z] = new Pixel(x, originalmap[x][z], z, originalid[x][z]);
			}
		}
		for (CompoundTag tag : blocks) {
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			IntTag idtag = tag.getIntTag("state");
			int x = pos.get(0).asInt();
			int y = pos.get(1).asInt();
			int z = pos.get(2).asInt();
			if (y < originalmap[x][z]) {
				pixelmap[x][z].cntun();
				underblockid = idtag.asInt();
			}
		}
		System.out.println(String.format("UnderBlock:%d:%s", underblockid, nameOf(underblockid)));
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		ListTag<CompoundTag> palette = cpt.getListTag("palette").asCompoundTagList();
		int[] cntunmin = new int[palette.size()];
		Arrays.fill(cntunmin, 1 << 30);
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				Pixel pix = pixelmap[x][z];
				pix.init(pixelmap);
				cntunmin[pix.id] = Math.min(cntunmin[pix.id], pix.under);
			}
		}
		for (int i = 0; i < cntunmin.length; i++) {
			if (cntunmin[i] > 0) {
				underneeds.add(i);
				System.out.println(String.format("UnderNeed:%d:%s", i, nameOf(i)));
			}
		}
		managers = new LineManager[X];
		for (int x = 0; x < X; x++) {
			managers[x] = new LineManager(x);
		}
		sortinglist = new ArrayList<LineManager>(X);
		for (LineManager lm : managers)
			sortinglist.add(lm);
	}

	int solve(final int mode, final int limit, final int threshold) {
		int changecnt = 0;
		sortinglist.forEach((o) -> {
			o.setMode(mode, limit);
		});
		sortinglist.sort(null);
		while (sortinglist.get(0).update > threshold) {
			sortinglist.get(0).dequeue();
			sortinglist.sort(null);
			changecnt++;
			if (changecnt % 1000 == 0) {
				System.out.println(changecnt + " moves... ");
			}
		}
		return changecnt;
	}

	private void fixconnectness() {
		int connect = (int) (Math.random() * Integer.MAX_VALUE);
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				if (pixelmap[x][z].y == 0)
					pixelmap[x][z].recconnect(connect);
			}
		}
		ArrayList<Pixel> connectneeds = new ArrayList<Pixel>();
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				if (!pixelmap[x][z].connected(connect))
					connectneeds.add(pixelmap[x][z]);
			}
		}
		System.out.println(String.format("Fix Connectness: %d", connectneeds.size()));
		Collections.sort(connectneeds, Comparator.comparing((o) -> {
			return o.y;
		}));
		Queue<Pixel> connectqueue = new ArrayDeque<Pixel>(connectneeds);
		while (!connectqueue.isEmpty()) {
			Pixel pix = connectqueue.poll();
			boolean ok = pix.catchconnect(connect);
			if (!ok)
				connectqueue.add(pix);
		}
	}

	private void editnbt() {
		int outputy = 0;
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				outputy = Math.max(pixelmap[x][z].y + 1, outputy);
			}
		}
		size.get(1).setValue(outputy);
		System.out.println(String.format("Size (%d, %d, %d) -> (%d, %d, %d)", X, Y, Z, X, size.get(1).asInt(), Z));

		boolean[][] schematic = new boolean[X][Z];
		ArrayDeque<Integer> underpool = new ArrayDeque<Integer>();
		int pixels = 0;
		int sameunder = 0;
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag tag = blocks.get(i);
			IntTag idtag = tag.getIntTag("state");
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int z = pos.get(2).asInt();
			Pixel pix = pixelmap[x][z];
			if (idtag.asInt() != pix.id) {
				underpool.add(i);
			} else {
				if (schematic[x][z]) {
					underpool.add(i);
					sameunder++;
				} else {
					pos.get(1).setValue(pix.y);
					schematic[x][z] = true;
					pixels++;
				}
			}
		}
		System.out.println(X * Z + "  " + pixels+"  "+sameunder);
		int bu = 0;
		int ou = 0;
		System.out.println(String.format("bu:%d ou:%d blo:%d que:%d", bu, ou, blocks.size(), underpool.size()));
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				Pixel pix = pixelmap[x][z];
				bu += pix.under;
				pix.optun(underneeds);
				ou += pix.under;
			}
		}
		System.out.println(String.format("bu:%d ou:%d blo:%d que:%d", bu, ou, blocks.size(), underpool.size()));
		System.out.println(
				String.format("UnderBlocks: %d -> %d (%s off)", bu, ou, (int) ((1f - (float) ou / bu) * 100) + "%"));
		for (int i = bu; i < ou; i++) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("state", underblockid);
			tag.putIntArray("pos", new int[] { 0, -1, 0 });
			underpool.add(blocks.size());
			blocks.add(tag);
		}
		System.out.println(String.format("bu:%d ou:%d blo:%d que:%d", bu, ou, blocks.size(), underpool.size()));
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				Pixel pix = pixelmap[x][z];
				int need = pix.under;
				for (int i = 0; i < need; i++) {
					CompoundTag tag = blocks.get(underpool.poll());
					ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
					pos.get(0).setValue(x);
					pos.get(1).setValue(pix.y - 1 - i);
					pos.get(2).setValue(z);
				}
			}
		}
		System.out.println(String.format("bu:%d ou:%d blo:%d que:%d", bu, ou, blocks.size(), underpool.size()));
		while (!underpool.isEmpty()) {
			blocks.remove(underpool.pollLast());
		}
		System.out.println(String.format("bu:%d ou:%d blo:%d que:%d", bu, ou, blocks.size(), underpool.size()));
	}

	private void write(File file) throws IOException {
		String newname = String.format("%s-optimized.nbt", file.getName().substring(0, file.getName().length() - 4));
		NBTUtil.write(rawtag, newname);
		System.out.println("Optimize Completed!");
		JOptionPane.showMessageDialog(null, "Complete! \n " + newname);
	}

	void pixverify() {
		try {
			for (int x = 0; x < X; x++) {
				for (int z = 0; z < Z; z++) {
					pixelmap[x][z].verify();
				}
			}
		} catch (Exception e) {
			System.out.println("Verify failed");
			e.printStackTrace();
		}
	}

	int difficulty() {
		int difficulty = 0;
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				difficulty += pixelmap[x][z].diff(0);
			}
		}
		return difficulty;
	}

	class LineManager implements Comparable<LineManager> {
		int x;
		Operation best;
		int update;
		int limit = 0;
		int mode = 0;

		public LineManager(int x) {
			this.x = x;
		}

		void refresh() {
			update = -1;
			for (int z = 0; z < Z; z++) {
				Pixel pix = pixelmap[x][z];
				Set<Integer> al = new HashSet<Integer>();
				pix.addal(mode, limit, al);
				for (int a : al) {
					int num = pix.checkup(a, mode);
					if (update < num) {
						update = num;
						best = new Operation(pix, a);
					} else if (update == num) {
						if (best.target.y > pix.y) {
							best = new Operation(pix, a);
						}
					}
				}
			}
		}

		void dequeue() {
			best.operate();
			refresh();
			if (x > 1)
				managers[x - 1].refresh();
			if (x < X - 1)
				managers[x + 1].refresh();
		}

		public void setMode(int mode, int limit) {
			this.mode = mode;
			this.limit = limit;
			refresh();
		}

		@Override
		public int compareTo(NBTOptimizer.LineManager o) {
			return o.update - this.update;
		}

		@Override
		public String toString() {
			return String.format("%d-%d", x, update);
		}
	}
}

class Operation {
	final Pixel target;
	final int amount;

	public Operation(Pixel target, int amount) {
		this.target = target;
		this.amount = amount;
	}

	void operate() {
		target.operateup(amount);
	}
}
