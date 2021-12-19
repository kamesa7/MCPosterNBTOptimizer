import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Queue;

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

	static int YLIMIT = 100;
	static int MOVELIMIT = 5;
	static int THRESHOLD = 5;
	static int SECONDMOVELIMIT = 5;
	static int SECONDTHRESHOLD = 5;
	static boolean LOG = false;
	static String UNDERBLOCK = "";

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
	boolean[][][] schematic;

	public NBTOptimizer(File file) throws IOException {

		loadProperties();
		load(file);
		construct();
		verify();
		int bd = difficulty();
		System.out.println("Difficulty: " + difficulty());

		/**
		 * Solving
		 */
		System.out.println("Optimizer Computing...");
		System.out.println("phase 1:");
		System.out.println(solve(0, MOVELIMIT, THRESHOLD) + " moves operated");
		System.out.println("phase 2:");
		System.out.println(solve(-1, SECONDMOVELIMIT, SECONDTHRESHOLD) + " moves operated");
		System.out.println("phase 3:");
		System.out.println(solve(1, SECONDMOVELIMIT, SECONDTHRESHOLD) + " moves operated");
		System.out.println("phase 4:");
		System.out.println(solve(0, MOVELIMIT, THRESHOLD) + " moves operated");

		verify();
		fixconnectness();
		makeschematic();
		optimizeunders();
		verify();
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
		LOG = Boolean.parseBoolean(properties.getProperty("Log"));
		UNDERBLOCK = properties.getProperty("UnderBlock");
	}

	private void load(File file) throws IOException {
		rawtag = NBTUtil.read(file);
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		ListTag<CompoundTag> palette = cpt.getListTag("palette").asCompoundTagList();
		blocks = cpt.getListTag("blocks").asCompoundTagList();

		for (int i = 0; i < palette.size(); i++) {
			CompoundTag tag = palette.get(i);
			String blockname = tag.getString("Name");
			if (blockname.equals(UNDERBLOCK)) {
				underblockid = i;
			}
		}
		if (underblockid < 0) {
			System.out.println("##NotFound UnderBlock##");
		}

		size = cpt.getListTag("size").asIntTagList();
		X = size.get(0).asInt();
		Y = size.get(1).asInt();
		Z = size.get(2).asInt();
		System.out.println(String.format("Loaded: %s  size:(%d, %d, %d)  blocks: %d  underblockID: %d", file.getName(),
				X, Y, Z, blocks.size(), underblockid));
	}

	private void construct() {
		int[][] originalmap = new int[X][Z];
		pixelmap = new Pixel[X][Z];
		for (CompoundTag tag : blocks) {
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int y = pos.get(1).asInt();
			int z = pos.get(2).asInt();
			originalmap[x][z] = Math.max(originalmap[x][z], y);
		}
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				pixelmap[x][z] = new Pixel(x, originalmap[x][z], z);
			}
		}
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				pixelmap[x][z].init(pixelmap);
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
		int connect = 1;
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
		System.out.println(String.format("Fixed Connectness: %d", connectneeds.size()));
		Collections.sort(connectneeds, Comparator.comparing(Pixel::getY).reversed());
		Queue<Pixel> connectqueue = new ArrayDeque<Pixel>(connectneeds);
		while (!connectqueue.isEmpty()) {
			Pixel pix = connectqueue.poll();
			boolean ok = pix.catchconnect(connect);
			if (!ok)
				connectqueue.add(pix);
		}
	}

	private void makeschematic() {
		int outputy = 0;
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				outputy = Math.max(pixelmap[x][z].y + 1, outputy);
			}
		}
		System.out.println(String.format("Size (%d, %d, %d) -> (%d, %d, %d)", X, Y, Z, X, outputy, Z));
		size.set(1, new IntTag(outputy));

		schematic = new boolean[X][outputy][Z];
		// visible blocks
		for (CompoundTag tag : blocks) {
			IntTag idtag = tag.getIntTag("state");
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int z = pos.get(2).asInt();
			int ny = pixelmap[x][z].y;
			if (idtag.asInt() != underblockid) {
				pos.set(1, new IntTag(ny));
				schematic[x][ny][z] = true;
			}
		}

		List<Integer> reminds = new ArrayList<Integer>();
		// under blocks
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag tag = blocks.get(i);
			IntTag idtag = tag.getIntTag("state");
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int z = pos.get(2).asInt();
			int ny = pixelmap[x][z].y;
			if (idtag.asInt() == underblockid) {
				int undery = Math.max(0, ny - 1);
				if (schematic[x][undery][z]) {
					undery = Math.max(0, undery - 1);
					if (schematic[x][undery][z]) { // deny 3 blocks or doubling
						reminds.add(i);
						continue;
					}
				}
				pos.set(1, new IntTag(undery));
				schematic[x][undery][z] = true;
			}
		}
		for (int i = reminds.size() - 1; i >= 0; i--) {
			blocks.remove(reminds.get(i));
		}
	}

	private void optimizeunders() {
		List<Integer> reminds = new ArrayList<Integer>();
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag tag = blocks.get(i);
			IntTag idtag = tag.getIntTag("state");
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int y = pos.get(1).asInt();
			int z = pos.get(2).asInt();
			if (idtag.asInt() == underblockid) {
				if (z - 1 < 0 || z + 1 >= Z)
					continue;
				if (x - 1 < 0 || x + 1 >= X)
					continue;
				if (!schematic[x][y][z - 1] && !schematic[x][y][z + 1] && y != 0 && !schematic[x - 1][y][z]
						&& !schematic[x + 1][y][z]) {
					reminds.add(i);
					schematic[x][y][z] = false;
				}
			}
		}
		for (int i = reminds.size() - 1; i >= 0; i--) {
			blocks.remove(reminds.get(i));
		}
		System.out.println("Removed Under: " + reminds.size());
	}

	private void write(File file) throws IOException {
		String newname = String.format("%s-optimized.nbt", file.getName().substring(0, file.getName().length() - 4));
		NBTUtil.write(rawtag, newname);
		System.out.println("Optimize Completed!");
		JOptionPane.showMessageDialog(null, "Complete! \n " + newname);
	}

	void verify() {
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
			for (int a = limit; a >= 1; a--) {
				for (int z = 0; z < Z; z++) {
					Pixel pix = pixelmap[x][z];
					int num = pix.checkup(a, mode);
					if (update < num) {
						update = num;
						best = new Operation(pix, a);
					} else if (update == num) {
						if (best.target.y < pix.y) {
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
