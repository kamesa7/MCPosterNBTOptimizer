import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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

	final int X;
	final int Y;
	final int Z;
	final int[][] originalmap;
	final Pixel[][] pixelmap;

	LineManager[] managers;
	List<LineManager> sorted;

	public NBTOptimizer(File file) throws IOException {
		Properties properties = new Properties();
		properties.load(new FileReader(new File("settings.properties")));
		YLIMIT = Integer.parseInt(properties.getProperty("Ylimit"));
		MOVELIMIT = Integer.parseInt(properties.getProperty("Warplimit"));
		THRESHOLD = Integer.parseInt(properties.getProperty("Threshold"));
		SECONDMOVELIMIT = Integer.parseInt(properties.getProperty("SecondWarplimit"));
		SECONDTHRESHOLD = Integer.parseInt(properties.getProperty("SecondThreshold"));
		LOG = Boolean.parseBoolean(properties.getProperty("Log"));
		UNDERBLOCK = properties.getProperty("UnderBlock");
		/**
		 * Loading
		 */
		NamedTag rawtag = NBTUtil.read(file);
		System.out.println(rawtag.getName());
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		System.out.println(cpt.keySet());
		int underblockid = -1;
		ListTag<CompoundTag> palette = cpt.getListTag("palette").asCompoundTagList();
		for (int i = 0; i < palette.size(); i++) {
			CompoundTag tag = palette.get(i);
			String blockname = tag.getString("Name");
			if (blockname.equals(UNDERBLOCK)) {
				underblockid = i;
				System.out.println("UnderBlockID: " + i);
			}
		}
		ListTag<IntTag> size = cpt.getListTag("size").asIntTagList();
		X = size.get(0).asInt();
		Y = size.get(1).asInt();
		Z = size.get(2).asInt();
		System.out.println(String.format("original size (%d, %d, %d)", X, Y, Z));
		ListTag<CompoundTag> blocks = cpt.getListTag("blocks").asCompoundTagList();
		System.out.println(blocks.size() + " blocks used");

		/**
		 * Constructing
		 */
		originalmap = new int[X][Z];
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
		sorted = new ArrayList<LineManager>(X);
		for (LineManager lm : managers)
			sorted.add(lm);
		/**
		 * Solving
		 */
		System.out.println(String.format("phase %d: %d moves operated", 1, solve(0, MOVELIMIT, THRESHOLD)));
		System.out
				.println(String.format("phase %d: %d moves operated", 2, solve(-1, SECONDMOVELIMIT, SECONDTHRESHOLD)));
		System.out.println(String.format("phase %d: %d moves operated", 3, solve(1, SECONDMOVELIMIT, SECONDTHRESHOLD)));
		System.out.println(String.format("phase %d: %d moves operated", 4, solve(0, MOVELIMIT, THRESHOLD)));

		/**
		 * Writing
		 */
		int outputy = 0;
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				outputy = Math.max(pixelmap[x][z].y + 1, outputy);
			}
		}
		System.out.println(String.format("optimized size (%d, %d, %d)", X, outputy, Z));
		size.set(1, new IntTag(outputy));
		boolean[][][] schematic = new boolean[X][outputy][Z];
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
		for (CompoundTag tag : blocks) {
			IntTag idtag = tag.getIntTag("state");
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int z = pos.get(2).asInt();
			int ny = pixelmap[x][z].y;
			if (idtag.asInt() == underblockid) {
				int undery = Math.max(0, ny - 1);
				if (schematic[x][undery][z]) {
					undery = Math.max(0, undery - 1);
				}
				pos.set(1, new IntTag(undery));
				schematic[x][undery][z] = true;
			}
		}
		List<Integer> reminds = new ArrayList<Integer>();
		for(int i=0;i<blocks.size();i++) {
			CompoundTag tag = blocks.get(i);
			IntTag idtag = tag.getIntTag("state");
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int y = pos.get(1).asInt();
			int z = pos.get(2).asInt();
			if (idtag.asInt() == underblockid) {
				if (z - 1 < 0 || z + 1 >= Z)
					continue;
				if (!schematic[x][y][z - 1] && !schematic[x][y][z + 1]) {
					reminds.add(i);
					schematic[x][y][z] = false;
				}
			}
		}
		for(int i=reminds.size()-1;i>=0;i--) {
			blocks.remove(reminds.get(i));
		}
		System.out.println("removeunder: " + reminds.size());
		String newname = String.format("%s-optimized.nbt", file.getName().substring(0, file.getName().length() - 4));
		NBTUtil.write(rawtag, newname);
		System.out.println("optimize complete");
		JOptionPane.showMessageDialog(null, "Complete! \n " + newname);
	}

	int solve(final int mode, final int limit, final int threshold) {
		int changecnt = 0;
		sorted.forEach((o) -> {
			o.setMode(mode, limit);
		});
		sorted.sort(null);
		while (sorted.get(0).update > threshold) {
			sorted.get(0).dequeue();
			sorted.sort(null);
			changecnt++;
		}
		return changecnt;
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
			update = 0;
			for (int a = limit; a >= 1; a--) {
				for (int z = 0; z < Z; z++) {
					int num = pixelmap[x][z].checkup(a, mode);
					if (update < num) {
						update = num;
						best = new Operation(pixelmap[x][z], a);
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
