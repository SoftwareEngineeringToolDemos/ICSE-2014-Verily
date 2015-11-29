#Using the Vagrant Script:
1. Download and Install Vagrant from [here](https://www.vagrantup.com/downloads.html)
2. Next, Download and Install VirtualBox from [here](https://www.virtualbox.org/wiki/Downloads)
3. Save the Vagrantfile on your machine and save it with name `Vagrantfile`. 
4. Now, Open Command prompt/Terminal and navigate to location where you saved the Vagrantfile and run command `vagrant up`.
5. This will download the Ubuntu OS and boot it up, it should auto-login.
6. Wait until all scripts in Command Prompt/ Terminal complete setting up environment and for the VM to reboot.

###Details of the built VM
As part of the load process, the following is loaded in the VM (as part of the Vagrant script)

1.Ubuntu Desktop 14.04</br>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Default username: vagrant</br>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Default password: vagrant</br>
2. Open JDK 7</br>
3. Maven</br>
4. Verily 

### References
1. [Vagrant shell documentation](https://docs.vagrantup.com/v2/provisioning/shell.html)
2. [Verily Documentation](http://docs.goverily.org/en/latest/quickstart.html#using-verily-in-vagrant)

### Acknowledgements
I would like to thank the uploader of the ubuntu desktop basebox "box-cutter/ubuntu1404-desktop" for making it availble on vagrant cloud.
