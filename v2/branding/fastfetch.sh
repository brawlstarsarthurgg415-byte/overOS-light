#!/bin/sh
# OverOS Fastfetch-style system identity display.
BLUE='\033[1;34m'
WHITE='\033[1;37m'
RESET='\033[0m'
printf "\n"
printf "  ${WHITE}       .-''''-.       ${RESET}  ${WHITE}Over${BLUE}OS${RESET}\n"
printf "  ${WHITE}     .'  ${BLUE}●${WHITE}   '.     ${RESET}  Sistema: OverOS Lite V2\n"
printf "  ${WHITE}    /  ${BLUE}●●●●${WHITE}  \\\   ${RESET}  Kernel:  "
uname -r
printf "  ${WHITE}    \\\  ${BLUE}●●●${WHITE}  /    ${RESET}  Arquitetura: "
uname -m
printf "  ${WHITE}     '.     .'     ${RESET}  Shell: BusyBox\n"
printf "  ${WHITE}       '-..-'       ${RESET}  Base: Linux + userland OverOS\n\n"
