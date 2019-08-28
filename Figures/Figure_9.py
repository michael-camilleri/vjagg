from datetime import datetime as dt
from datetime import timezone as tz
import matplotlib.pyplot as plt
import numpy as np
import csv
import os

DATA = 'idle_discharge'
COLORS = ('r', 'g', 'b', 'y', 'c', 'm', 'y', 'k')
POLY_ORDER = 1
STORE = '../resources'
TEXTSIZE = 24

if __name__ == '__main__':

    # Load the Data first
    with open(os.path.join('data/battery', DATA) + '.txt', 'r') as csv_file:
        raw_times = []
        percentages = []    # This will actually contain the change-points.
        charge_lvls = []    # This will be the raw charge levels
        current_charge = 100    # Start off with 100-charge.
        for row in csv.reader(csv_file, delimiter=' '):
            raw_times.append(int(row[0])/1000)
            if int(row[1]) != current_charge:
                current_charge = int(row[1])
                percentages.append(raw_times[-1])
            charge_lvls.append(int(row[2]))
        percentages.append(raw_times[-1]) # Store the last one!
        # Convert to 0-based time
        percentages = np.asarray(percentages) - raw_times[0]
        raw_times = np.asarray(raw_times) - raw_times[0]
    # Fit a line through the points.
    poly = np.polyfit(raw_times, charge_lvls, deg=POLY_ORDER)

    # Plot the Discharge level
    plt.figure(figsize=[15, 5])
    plt.plot(raw_times, charge_lvls, 'b', lw=2, label='Charge Level')
    plt.plot(raw_times, np.polyval(poly, raw_times), 'r--', lw=2, label='Linear Fit')
    per_strt = 0
    for c, perc in enumerate(percentages):
        plt.axvspan(per_strt, perc, facecolor=COLORS[c % len(COLORS)], alpha=0.5)
        per_strt = perc
    plt.xlabel('Time (hours)', fontsize=TEXTSIZE)
    plt.ylabel('Charge Level (\u00B5 Ah)', fontsize=TEXTSIZE)
    plt.gca().set_xlim([0, raw_times[-1]])
    plt.gca().set_xticks(np.arange(start=0, stop=raw_times[-1], step=28800))
    plt.gca().set_xticklabels([dt.fromtimestamp(s, tz.utc).strftime('%H:%M') for s in range(0, int(raw_times[-1]), 28800)])
    plt.gca().tick_params(labelsize=TEXTSIZE)
    plt.legend(fontsize=TEXTSIZE)
    plt.tight_layout()
    plt.savefig(os.path.join(STORE, 'idle_discharge.eps'), dpi='figure')
    plt.show()