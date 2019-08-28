from geopy.distance import vincenty as calc_dist
import matplotlib.pyplot as plt
import numpy as np
import csv
import os

DATA = 'gps_dynamics'
STORE = '../resources'
TEXTSIZE = 24
# Definition of streets with length
STREETS = {'DOMinic': 47.63,
           'FReDerick': 142.10,
           'ARChbishop': 48.49,
           'REPublic': 141.00,
           'STRaight': 95.74,
           'MeLiTa': 43.27,
           'iFRaN': 97.01,
           'JOhN': 41.64,
           'SouTH': 58.88,
           'ZEKka': 341.40,
           'THEatre': 58.36,
           'BAKery': 341.59}


class StreetRun:
    """
    Simple class to hold together street parameters
    """
    def __init__(self, street, file_name):
        self.mStr = street
        self.mFile = file_name
        self.mVelo = -1
        self.mStart = -1
        self.mEnd = -1
        self.mPoints = []


if __name__ == '__main__':

    # First parse the Data
    runs = []
    for street in STREETS:
        for filename in os.listdir(os.path.join('data/gps', DATA, street)):
            run = StreetRun(street, filename)
            with open(os.path.join('data/gps', DATA, street, filename), 'r') as csv_file:
                file_entries = csv.reader(csv_file, delimiter=' ')
                start = True  # Searching for start
                for row in file_entries:
                    if row[0] == 'P':
                        if start:
                            run.mStart = int(row[1])
                        else:
                            run.mEnd = int(row[1])
                        start = False
                    elif row[0] == 'L':
                        run.mPoints.append((int(row[1]), float(row[2]), float(row[3])))
            # Compute Velocity
            run.mVelo = STREETS[street] * 1000.0 / (run.mEnd - run.mStart)
            runs.append(run)

    # Now start the analysis
    mean_error = []
    std_error = []
    per95_error = []
    max_error = []

    for avg_size in range(1, 10):
        deviations = []  # Empty list of deviations
        for run in runs:
            for offset in range(avg_size):
                # Compute Average with Down-Sample
                downsampled = []  # Empty Downsampled points
                i = offset
                while i + avg_size <= len(run.mPoints):
                    time_val = sum(t for t, _, _ in run.mPoints[i:i + avg_size]) / float(avg_size)
                    latitude = sum(x for _, x, _ in run.mPoints[i:i + avg_size]) / float(avg_size)
                    longitud = sum(y for _, _, y in run.mPoints[i:i + avg_size]) / float(avg_size)
                    downsampled.append((time_val, latitude, longitud))
                    i += avg_size

                # Compute Velocities and deviations
                for i in range(1, len(downsampled)):
                    time_diff = downsampled[i][0] - downsampled[i - 1][0]  # Time Difference
                    start = (downsampled[i - 1][1], downsampled[i - 1][2])
                    end = (downsampled[i][1], downsampled[i][2])
                    velocity = calc_dist(start, end).meters * 1000.0 / time_diff  # Convert MS to S
                    deviations.append(abs(velocity - run.mVelo))
        mean_error.append(np.mean(deviations))
        std_error.append(np.std(deviations))
        per95_error.append(np.percentile(deviations, 95))
        max_error.append(max(deviations))

    # Finally Plot
    plt.figure(figsize=[15, 8])
    plt.plot(range(1, 10), mean_error, 'r', lw=3, label='Error Mean')
    plt.plot(range(1, 10), std_error, 'b-', lw=2, label='Error STD')
    plt.plot(range(1, 10), per95_error, 'b--', lw=2, label='Error 95th Percentile')
    plt.ylabel('Deviation (m/s)', fontsize=TEXTSIZE)
    plt.xlabel('Averaging Window Size (samples)', fontsize=TEXTSIZE)
    plt.gca().tick_params(labelsize=TEXTSIZE)
    plt.legend(loc=2, fontsize=TEXTSIZE)
    plt.twinx()
    plt.plot(range(1, 10), max_error, 'k--', lw=2.5, label='Error Max')
    plt.ylabel('Max Deviation (m/s)', fontsize=TEXTSIZE)
    plt.gca().tick_params(labelsize=TEXTSIZE)
    plt.legend(loc=1, fontsize=TEXTSIZE)
    plt.tight_layout()
    plt.savefig(os.path.join(STORE, 'gps_dynamics.eps'), dpi='figure')
    plt.show()