from datetime import datetime as dt
from datetime import timezone as tz
import matplotlib.pyplot as plt
import numpy as np
import csv
import os

DATA = 'comparative'
LEGENDS = ['With Battery-Saving', 'GPS Only']
COLORS = ['r', 'b']
STORE = '../resources'
TEXTSIZE = 24

if __name__ == '__main__':

    # Load the Data first
    raw_times = []
    charge_lvls = []    # This will be the raw charge levels
    for i, leg in enumerate(LEGENDS):
        raw_times.append([])
        charge_lvls.append([])
        with open(os.path.join('data/battery', DATA, str(i+1)) + '.txt', 'r') as csv_file:
            for row in csv.reader(csv_file, delimiter=' '):
                raw_times[-1].append(int(row[0])/1000)
                charge_lvls[-1].append(int(row[2]))
            # Convert to 0-based time
            raw_times[-1] = np.asarray(raw_times[-1]) - raw_times[-1][0]
    max_time = np.max([t[-1] for t in raw_times])

    # Plot everything
    plt.figure(figsize=[15, 5])
    for i, (tms, lvl) in enumerate(zip(raw_times, charge_lvls)):
        plt.plot(tms, lvl, COLORS[i], lw=2, label=LEGENDS[i])
    plt.xlabel('Time (hours)', fontsize=TEXTSIZE)
    plt.ylabel('Charge Level (\u00B5 Ah)', fontsize=TEXTSIZE)
    plt.gca().set_xlim([0, max_time])
    plt.gca().set_xticks(np.arange(start=0, stop=max_time, step=7200))
    plt.gca().set_xticklabels([dt.fromtimestamp(s, tz.utc).strftime('%H:%M') for s in range(0, int(max_time), 7200)])
    plt.gca().tick_params(labelsize=TEXTSIZE)
    plt.legend(fontsize=TEXTSIZE)
    plt.tight_layout()
    plt.savefig(os.path.join(STORE, 'comparative.eps'), dpi='figure')
    plt.show()