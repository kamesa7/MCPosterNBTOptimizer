import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;

public class HeightMapper {
	public static void main(String[] args) {
		try {
			if (args.length < 1) {
				FileDialog dialog = new FileDialog(new Frame());
				dialog.setMode(FileDialog.LOAD);
				dialog.setMultipleMode(false);
				dialog.setTitle("Select Litematic file");
				dialog.setVisible(true);
				new HeightMapper(dialog.getFiles()[0]);
			} else {
				new HeightMapper(new File(args[0]));
			}
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e);
		}
		System.exit(0);
	}

	int X;
	int Y;
	int Z;
	int underblockid1 = -1;
	int underblockid2 = -1;
	int underblockid3 = -1;
	long[] volume;
	int[][] height;
	String[] strpalette;
	NamedTag rawtag;

	public HeightMapper(File file) throws IOException {
		load(file);
	}

	private void load(File file) throws IOException {
		rawtag = NBTUtil.read(file);
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		System.out.println(cpt.keySet());
		System.out.println();
//		Tag<?> regions = cpt.get("Regions");
//		System.out.println(regions.getClass());
		System.out.println(cpt.getCompoundTag("Metadata"));
		CompoundTag regions = cpt.getCompoundTag("Regions");
		System.out.println(regions.keySet());
		for (String regkey : regions.keySet()) {
			CompoundTag subregion = regions.getCompoundTag(regkey);
			System.out.println(subregion.keySet());
			for (String subkey : subregion.keySet()) {
				Tag<?> reg = subregion.get(subkey);
				System.out.println(reg.getClass());
			}
			CompoundTag size = subregion.getCompoundTag("Size");
			ListTag<?> palette = subregion.getListTag("BlockStatePalette");
			System.out.println(size.valueToString());
			X = size.getIntTag("x").asInt();
			Y = size.getIntTag("y").asInt();
			Z = size.getIntTag("z").asInt();
			height = new int[Z][X];
			CompoundTag position = subregion.getCompoundTag("Position");
			System.out.println(position.valueToString());
			strpalette = new String[palette.size()];
			for (int i = 0; i < palette.size(); i++) {
				CompoundTag block = (CompoundTag) palette.get(i);
				String name = block.getString("Name");
				strpalette[i] = name;
//				System.out.println(i + " " + name);
				if (name.equals("minecraft:stone"))
					underblockid1 = i;
				if (name.equals("minecraft:dirt"))
					underblockid2 = i;
//				if (name.equals("minecraft:grass_block"))
//					underblockid3 = i;

			}
			System.out.println(String.format("Palettesize:%d", palette.size()));
			bitsPerEntry = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
			System.out.println(String.format("UnderBlock1:%d", underblockid1));
			System.out.println(String.format("UnderBlock2:%d", underblockid2));
			System.out.println(String.format("UnderBlock3:%d", underblockid3));
			volume = subregion.getLongArray("BlockStates");
			System.out.println(volume.length);
			System.out.println(Z * Y * X);
			System.out.println(subregion.get("PendingBlockTicks"));
			System.out.println(subregion.get("PendingFluidTicks"));
		}
//		for(int i=0;i<volume.length;i++) {
//			System.out.println(strpalette[getAt(i)]);
//		}
		int offset = 5;
		for (int z = 0; z < Z; z++) {
			for (int x = 0; x < X; x++) {
				for (int y = 0; y < Y; y++) {
					if (getAt(x, y, z) == underblockid1) {
						height[z][x] = y + offset;
					}
					if (getAt(x, y, z) == underblockid2) {
						height[z][x] = y + offset;
					}
					if (getAt(x, y, z) == underblockid3) {
						height[z][x] = y + offset;
					}
				}
			}
		}
		IntBuffer bmp = IntBuffer.allocate(Z * X);
		for (int z = 0; z < Z; z++) {
			for (int x = 0; x < X; x++) {
				int col;
				if(height[z][x]%2==0) {
					col = BGR(Math.pow((double) height[z][x] / Y, 0.75));
				}else {
					col = BGR(Math.pow((double) (height[z][x]-3) / Y, 0.75));
				}
				bmp.put(z * X + x, col);
			}
		}
		writeBmp(X, Z, bmp, "heightmap");
	}

	private int BGR(double num) {
		assert num >= 0 && num <= 1;
		double r = num >= 0.5 ? num * 2 - 1 : 0;
		double g = 1 - Math.abs(num - 0.5) * 2;
		double b = num <= 0.5 ? 1 - num * 2 : 0;
		return (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
	}

	public File writeBmp(int width, int height, IntBuffer bmp, String name) {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				bi.setRGB(x, y, bmp.get(y * width + x));
			}
		}
		try {
			File file = new File(String.format("%s.png", name));
			ImageIO.write(bi, "png", file);
			System.out.println("Wrote Image " + file.getName());
			return file;
		} catch (IOException e) {
			e.printStackTrace();
			Toolkit.getDefaultToolkit().beep();
			return null;
		}
	}

	int bitsPerEntry;
	long maxEntryValue;

	public int getAt(long index) {
		//
		maxEntryValue = (1L << bitsPerEntry) - 1L;
		//

		long startOffset = index * (long) this.bitsPerEntry;
		int startArrIndex = (int) (startOffset >> 6); // startOffset / 64
		int endArrIndex = (int) (((index + 1L) * (long) this.bitsPerEntry - 1L) >> 6);
		int startBitOffset = (int) (startOffset & 0x3F); // startOffset % 64

		if (startArrIndex == endArrIndex) {
			return (int) (this.volume[startArrIndex] >>> startBitOffset & this.maxEntryValue);
		} else {
			int endOffset = 64 - startBitOffset;
			return (int) ((this.volume[startArrIndex] >>> startBitOffset | this.volume[endArrIndex] << endOffset)
					& this.maxEntryValue);
		}
	}

	private int getAt(int x, int y, int z) {
		return getAt((long) y * X * Z + z * X + x);
	}
}
