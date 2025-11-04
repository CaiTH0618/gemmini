#!/bin/bash

help () {
  echo "Build a cycle-accurate Verilator simulator for RISCV Gemmini programs,"
  echo 'matching `customConfig` in `configs/GemminiCustomConfigs.scala`.'
  echo
  echo "Usage: $0 [-h|--help] [--debug] [-j [N]] [-t [M]] [-c [C]]"
  echo
  echo "Options:"
  echo " debug   Builds a Verilator simulator which generates waveforms. Without"
  echo "         this option, the simulator will not generate any waveforms."
  echo " j [N]   Allow N jobs at once. Default is 1."
  echo " t [M]   Build a Verilator simulator with M threads. Default is 1. Recommend 4."
  echo " c [C]   Specify the name of the target chipyard config. Default is CustomGemminiSoCConfig."
  exit
}

show_help=0
debug=""
j="1"
threads="1"
config="CustomGemminiSoCConfig"

while [ $# -gt 0 ] ; do
  case $1 in
    -h | --help) show_help=1 ;;
    --debug) debug="debug" ;;
    -j) j=$2; shift ;;
    -t) threads=$2; shift ;;
    -c) config=$2; shift ;;
  esac

  shift
done

if [ $show_help -eq 1 ]; then
 help
fi

cd ../../sims/verilator/
# make -j$j ${debug} CONFIG=CustomGemminiSoCConfig
make -j$j ${debug} CONFIG=${config} VERILATOR_THREADS=${threads} NUMACTL=1

