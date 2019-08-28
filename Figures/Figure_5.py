import matplotlib.pyplot as plt
import math
import csv
import os

DATA = ['table', 'pocket', 'meeting', 'bus', 'walking', 'car']
COLORS = ('r', 'g', 'b', 'c', 'm', 'y', 'k')
STORE = '../resources'
GRAVITY = 9.81
TEXTSIZE = 25


if __name__ == '__main__':

    for i, dname in enumerate(DATA):
        # Load the Data
        with open(os.path.join('data/accelerometer', dname) + '.txt', 'r') as csv_file:
            # Use A CSV Reader
            acc_file = csv.reader(csv_file, delimiter=' ')
            # Get the starting time
            start_time = float(next(acc_file)[1])
            csv_file.seek(0)
            # Fill in the times/accelerations
            times = []; accelerations = []
            for row in acc_file:
                times.append((float(row[1]) - start_time) / 1000.0)
                accelerations.append(abs(math.sqrt(float(row[2])**2 + float(row[3])**2 + float(row[4])**2) - GRAVITY))
        # Create Figure
        fig = plt.figure(figsize=[15, 5])
        plt.plot(times[:300], accelerations[:300], COLORS[i], lw=3)
        plt.xlabel('Time (s)', fontsize=TEXTSIZE)
        plt.ylabel('Acc - 9.81 ($m/s^2$)', fontsize=TEXTSIZE)
        plt.gca().axis([0, times[300], -0.5, 20])
        plt.gca().tick_params(labelsize=TEXTSIZE)
        plt.tight_layout()
        plt.savefig(os.path.join(STORE, 'acc_profile_{}.eps'.format(dname)), dpi='figure')

    plt.show()
