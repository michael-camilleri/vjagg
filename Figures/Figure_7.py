import matplotlib.pyplot as plt
import numpy as np
import csv
import os

DATA = 'gps_noise'
COLORS = ['r', 'b', 'k']
STORE = '../resources'
TEXTSIZE = 24

if __name__ == '__main__':

    # Load the Data
    with open(os.path.join('data/gps', DATA) + '.txt', 'r') as csv_file:
        fix_time = []; fix_lat = []; fix_long = []
        sat_time = []; sat_numb = []
        file_entries = csv.reader(csv_file, delimiter=' ')
        p = False  # Ping not yet encountered - only read between pings
        for row in file_entries:
            if p:
                if row[0] == 'L':
                    fix_time.append(int(row[1]) / 1000)
                    fix_lat.append(float(row[2])*3600)
                    fix_long.append(float(row[3])*3600)
                elif row[0] == 'S':
                    sat_time.append(int(row[1]) / 1000)
                    sat_numb.append(int(row[2]))
            elif row[0] == 'P':
                p = True
    # Convert as appropriate
    fix_time = np.asarray(fix_time)
    fix_lat = np.asarray(fix_lat)
    fix_long = np.asarray(fix_long)
    sat_time = np.asarray(sat_time)
    sat_numb = np.asarray(sat_numb)
    start = min(fix_time[0], sat_time[0])

    # Now Plot
    plt.figure(figsize=[15, 8])
    plt.plot(fix_time - start, np.abs(fix_lat - np.mean(fix_lat)), COLORS[0], lw=3, label='Latitude')
    plt.plot(fix_time - start, np.abs(fix_long - np.mean(fix_long)), COLORS[1], lw=3, label='Longitude')
    plt.ylabel('Seconds from Minimum', fontsize=TEXTSIZE)
    plt.xlabel('Time (s)', fontsize=TEXTSIZE)
    plt.gca().tick_params(labelsize=TEXTSIZE)
    plt.legend(loc=2, fontsize=TEXTSIZE)
    plt.twinx()
    plt.plot(sat_time - start, sat_numb, COLORS[2], label='Satellites')
    plt.legend(loc=1, fontsize=TEXTSIZE)
    plt.ylabel('Number of Satellites', fontsize=TEXTSIZE)
    plt.gca().tick_params(labelsize=TEXTSIZE)
    plt.tight_layout()
    plt.savefig(os.path.join(STORE, 'gps_noise.eps'), dpi='figure')
    plt.show()
