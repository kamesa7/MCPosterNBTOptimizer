public class Pixel {
	final int x;
	int y;
	final int z;
	Pixel upp;
	Pixel upm;
	Pixel downp;
	Pixel downm;
	Pixel samep;
	Pixel samem;
	Pixel sidep;
	Pixel sidem;

	Pixel(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	private int nexty(int ny) {
		return Math.max(ny + 1, y);
	}

	private int diff() {
		int p = sidep != null ? transfunc(y, sidep.y) : 0;
		int m = sidem != null ? transfunc(y, sidem.y) : 0;
		return p + m;
	}

	private int ifup(int ny) {
		if (ny >= NBTOptimizer.YLIMIT)
			return 1 << 20;
		int p = sidep != null ? transfunc(ny, sidep.y) : 0;
		int m = sidem != null ? transfunc(ny, sidem.y) : 0;
		return p + m;
	}

	private int transfunc(int thisy, int sidey) {
		int d = sidey - thisy;
		int ad = Math.abs(d);
		switch (ad) {
		case 0:
			break;
		case 1:
			ad = 2;
			break;
		default:
			ad = 3;
		}
		return ad;
	}

	int checkup(int a) {
		if (samep != null && samem != null)
			return 0;

		int now = recdiff(null);
		int ifup = recifup(y + a, null);

		if (ifup < now) {
			return now - ifup;
		}
		return 0;
	}

	void operateup(int a) {
		int now = recdiff(null);
		int ifup = recifup(y + a, null);
		if (NBTOptimizer.LOG)
			System.out.println(String.format("(%d,%d) y: %d -> %d  (%d -> %d)", x, z, y, y + a, now, ifup));
		recsetup(y + a, null);
	}

	private int recdiff(Pixel from) {
		int diff = diff();
		if (upp != null && upp != from)
			diff += upp.recdiff(this);
		if (upm != null && upm != from)
			diff += upm.recdiff(this);
		if (samep != null && samep != from)
			diff += samep.recdiff(this);
		if (samem != null && samem != from)
			diff += samem.recdiff(this);
		return diff;
	}

	private int recifup(int ny, Pixel from) {
		int diff = ifup(ny);
		if (upp != null && upp != from)
			diff += upp.recifup(upp.nexty(ny), this);
		if (upm != null && upm != from)
			diff += upm.recifup(upm.nexty(ny), this);
		if (samep != null && samep != from)
			diff += samep.recifup(ny, this);
		if (samem != null && samem != from)
			diff += samem.recifup(ny, this);
		return diff;
	}

	private void recsetup(int ny, Pixel from) {
		this.y = ny;
		if (upp != null && upp != from)
			upp.recsetup(upp.nexty(y), this);
		if (upm != null && upm != from)
			upm.recsetup(upm.nexty(y), this);
		if (samep != null && samep != from)
			samep.recsetup(y, this);
		if (samem != null && samem != from)
			samem.recsetup(y, this);
	}

	void init(Pixel[][] map) {
		try {
			this.sidem = map[x - 1][z];
		} catch (IndexOutOfBoundsException e) {
		}
		try {
			this.sidep = map[x + 1][z];
		} catch (IndexOutOfBoundsException e) {
		}

		try {
			Pixel up = map[x][z + 1];

			if (up.y > y) {
				upp = up;
			} else if (up.y < y) {
				downp = up;
			} else {
				samep = up;
			}
		} catch (IndexOutOfBoundsException e) {
		}

		try {
			Pixel down = map[x][z - 1];
			if (down.y > y) {
				upm = down;
			} else if (down.y < y) {
				downm = down;
			} else {
				samem = down;
			}
		} catch (IndexOutOfBoundsException e) {
		}
	}

	@Override
	public String toString() {
		return String.format("(%d, %d, %d)  upp:%d  upm:%d  sidep:%d  sidem:%d", x, y, z, upp != null ? upp.y : null,
				upm != null ? upm.y : null, sidep != null ? sidep.y : null, sidem != null ? sidem.y : null);
	}
}