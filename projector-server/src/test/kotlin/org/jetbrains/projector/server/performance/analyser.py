import re
import os
import numpy as np
import matplotlib.pyplot as plt


def plot_graphic(dots):
    x = np.arange(0, len(dots))
    plt.figure(figsize=(100, 10))
    plt.plot(x, dots, 'o')
    plt.plot(x, dots)
    plt.show()


def log_parser():
    pattern = r'[^CPU load:]*\d$'
    cpu_usage = np.zeros(0, int)
    with open('logback.log') as f:
        for line in f:
            result = re.findall(pattern, line)
            if result:
                cpu_usage = np.append(cpu_usage, result, axis=0)
    cpu_usage = cpu_usage.astype(np.float_)
    cpu_no_zeros = cpu_usage[~np.isin(cpu_usage, 0.)]
    print("CPU", cpu_usage)
    print("Length", len(cpu_usage))
    print("AVG with zeros", np.mean(cpu_usage))
    print("new", cpu_no_zeros)
    print("length no zeros", len(cpu_no_zeros))
    print("AVG no zeros", np.mean(cpu_no_zeros))
    plot_graphic(cpu_usage)


def main():
    # draft script
    # todo: update path for server and client root dirs
    os.system("./gradlew runIdeaServer")

    os.system("cd projector-client")
    os.system("./gradlew startLoad")

    log_parser()


if __name__ == '__main__':
    main()
