import matplotlib.pyplot as plt
import numpy as np
import csv
import os

DATA = 'transition'
COLORS = ('r', 'b')
STORE = '../resources'
GRAVITY = 9.81
TEXTSIZE = 24


if __name__ == '__main__':

    # Load the Data
    with open(os.path.join('data/accelerometer', DATA) + '.txt', 'r') as csv_file:
        # Use A CSV Reader
        acc_file = csv.reader(csv_file, delimiter=' ')
        # Get the starting time
        start_time = float(next(acc_file)[0])
        csv_file.seek(0)
        # Fill in the times/accelerations
        times = []; accelerations = []
        for row in acc_file:
            times.append((float(row[0]) - start_time) / 1000.0)
            accelerations.append(np.sqrt(float(row[1])**2 + float(row[2])**2 + float(row[3])**2))
        # Create Figure
        fig = plt.figure(figsize=[15, 8])
        plt.plot(times, accelerations, COLORS[0], lw=3, label='Raw')
        plt.plot(times, abs(np.asarray(accelerations) - GRAVITY), lw=3, label='ABS(Acc - g)')
        plt.axvline(x=9, lw=5, color='g', ls='--')
        plt.xlabel('Time (s)', fontsize=TEXTSIZE)
        plt.ylabel('Acceleration ($m/s^2$)', fontsize=TEXTSIZE)
        plt.gca().axis([0, times[-1], -0.5, 21])
        plt.gca().tick_params(labelsize=TEXTSIZE)
        plt.legend(fontsize=TEXTSIZE)
        plt.tight_layout()
        plt.savefig(os.path.join(STORE, 'acc_profile_transition.eps'), dpi='figure')

    plt.show()
