# Transparency4Ortho
Transparency4Ortho is a tool that helps making (non-motorway) roads in X-Plane 11 transparent on scenery for which ortho-imagery (typically generated by Ortho4XP) is present. This allows the roads from the actual photo scenery to be visible instead of the bold grayish roads that X-Plane normally draws on the ground, allowing for a much more consistent and realistic look.

The main difference between Transparency4Ortho and other methods is that Transparency4Ortho applies these changes *only* on top of ortho-covered ground tiles. It is no longer necessary to switch transparent roads off or on globally, and flying from ortho-covered into uncovered areas is no longer a problem.

## How does it look?
Here's a screenshot of Queenstown ***without*** Transparency4Ortho applied:
![Queenstown without Transparency4Ortho](https://i.imgur.com/9XiEOpx.jpg)

And here the scene ***with*** Transparency4Ortho: 
![Queenstown with Transparency4Ortho](https://i.imgur.com/6hpx5jt.jpg)

[Here are more impressions from different areas of the world](https://imgur.com/gallery/7lyOrTj)

## How does it work?
The tool automates the following steps to achieve transparent roads:
1. Transparency4Ortho creates a copy of your X-Planes default library `X-Plane 11/Resources/default scenery/1000 roads` under `X-Plane 11/Custom Scenery/Transparency4Orth`. 
2. In the new folder, it automatically applies some changes to the `roads.net` and `roads_EU.net` to make (non-motorway) roads invisible, based on solutions discussed on the [X-Plane.org Forums](https://forums.x-plane.org/index.php?/forums/topic/140017-transparent-roads-with-cars/) - but you can apply your own transparency mod as well, if you prefer to have it your own way.
3. It publishes these modified road-network definitions in a `library.txt` file.
4. It scans your X-Plane sceneries for tiles that have ortho-overlays *and* ortho-imagery present. 
5. It modifies the overlays of those tiles to use the road-network definition from Transparency4Ortho instead of the global ones.

# Instructions
## Installation instructions
Currently, Transparency4Ortho is a command-line tool only, there is no graphical user interface (GUI). It uses Java and should work on Windows, Mac, and Linux - but I have only tested on Windows so far and need feedback for the other platforms.

1. If you don't have a Java 11 runtime on your system, download and install one for your platform - for example from [AdoptOpenJDK](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot). 
2. Download the latest *jar*-Release of Transparency4Ortho from the [Release-Page](https://github.com/melb00m/Transparency4Ortho/releases).
3. Extract the file to any location (doesn't need to be in the X-Plane folder - in fact, I would choose a different place).
4. As this is a command-line based tool, you need to open a shell to use it. In Windows, the easiest way to achieve that is to navigate to the folder into which you unzipped Transparency4Ortho in the Explorer, and then navigate to the `bin` folder inside of that one - you should see two `Transparency4Ortho` files in there. Now click on the location bar in Explorer and simply type `cmd` - this will open the CMD-shell in this folder.
5. Type `Transparency4Ortho --help` - you should see an overview of parameters and options to use with the tool. If everything is fine so far, move on to the usage instructions below.

## Usage instructions
If you want the transformation process to be fully automatic, Transparency4Ortho can scan your whole `scenery_pack.ini` file to auto-detect *all* ortho-tiles you have installed. Or you can tell it to transform only certain overlay-directories, if you want to try a smaller area first.

### Fully automatic
Simply run the command below and follow the instructions on the screen:

`Transparency4Ortho <path-to-xplane>` 

For example: 

`Transparency4Ortho "E:\Games\X-Plane 11"`) 

( Note that the quotation marks around the folder-names are a thing of the Windows CMD shell, not a syntax required by Transparency4Ortho. Other shells and operating systems do it differently. )

### Manual selection of overlays:
Used like the automatic command above, but after the X-Plane folder specify any number of overlay-folders you want to process. 

`Transparency4Ortho <path-to-xplane> <path-to-overlay> [<path-to-more-overlays>...]`

For example:

`Transparency4Ortho "E:\Games\X-Plane 11" "E:\Games\X-Plane 11\Custom Scenery\simHeaven_X-Europe-7-network" "L:\Ortho4XP\Overlays\Lyndiman NZ"`

Note that you can also use a folder that contains multiple overlay-folders in it's file-structure below as a parameter - Transparency4Ortho will detect those. You can not, however, use only certain tiles inside an overlay-folder. In other words, if you have this structure:

> ├───Forkboy US  
> │   ├───yOrtho4XP_Arizona  
> │   │   └───Earth nav data  
> │   │       ├───+30-110  
> │   │       └───+30-120  
> │   ├───yOrtho4XP_California  
> │   │   └───Earth nav data  
> │   │       ├───+30-120  
> │   │       ├───+30-130  
> │   │       └───+40-130  

You **can** use `Forkboy US` (to include both) or just `Forkboy US/yOrtho4XP_Arizona` as a parameter, but you **can not** use `Forkboy US/yOrtho4XP_Arizona/Earth nav data/+30-110`.

### Backups of modified overlays
Transparency4Ortho will automatically create backups of overlays it modifies under `<X-Plane-Folder>/Transparency4Ortho/Backups/<date-time>/`. You can choose another folder using the `-b`-option, but you can not switch them off completely.

### Influencing the ortho auto-detection
If Transparency4Ortho does not detect some ortho-tiles or scenery automatically, or if it detects folders that are actually not orthos, you can influence the behavior of the scanner by simply creating (empty) files inside those directories with specified names.

#### Include a folder as ortho-scenery
Create a file named `Transparency4Ortho.Ortho.Include` in the scenery-directory you want to include as ortho-scenery.

#### Include a folder as ortho-overlay
Create a file named `Transparency4Ortho.Overlay.Include` in the scenery-directory you want to include as ortho-overlay.

#### Exclude a folder as ortho-scenery
Create a file named `Transparency4Ortho.Ortho.Exclude` in the scenery-directory you want to exclude from the scan.

#### Exclude a folder as ortho-overlay
Create a file named `Transparency4Ortho.Overlay.Exclude` in the scenery-directory you want to exclude from the scan.

### Additional options
Run `Transparency4Ortho --help` to see a list of commands. 

# FAQ
## Which versions of X-Plane are supported?
I have currently tested X-Plane 11.41 as well as 11.50b6 with Vulkan.

## The tool tells me that there is a checksum mismatch
Before making a copy of the original roads-library from `X-Plane 11/Resources/default scenery/1000 roads` into it's own library, Transparency4Ortho verifies that the `roads.net` and `roads_EU.net` are in the default state that ships with the X-Plane versions mentioned above. The reasoning behind this is to detect any changes that might have already been applied to these files, such as a global transparency-mod. Applying an automated mod to an already modded file might lead to unexpected results, that is why there is this safeguard.

You can easily restore the original version of those files by re-running the X-Plane installer over your installation. That said, if you don't want to do that or still have problems after restoring (please let me know!), you can also skip this error and give it a shot. The relevant commands can be displayed using `Transparency4Ortho --help`.

## Does it run on Windows / Mac / Linux
Transparency4Ortho is developed using Java, so it should run on all of these platforms. Transparency4Ortho relies on the `DSFTool` which is part of the official [X-Plane Developer Command-Line Tools](https://developer.x-plane.com/tools/xptools/). They are also available for these three platforms Transparency4Ortho will (attempt to) download them automatically.

## Can I run it without installing Java?
Currently not, but I am working on versions that bundle the necessary Java runtime for each platform. Currently you need to download a runtime of Java 11 (or higher) yourself. I recommend [AdoptOpenJDK](https://adoptopenjdk.net/).

## Will there be a version with a graphical user interface (GUI)
Maybe, if there really is a demand for it.

## Can this destroy my scenery?
First of all, this is an early release, so errors can happen - use it on your own risk. That said, the only modifying changes Transparency4Ortho makes to existing files in your X-Plane installation is to *overlays*, not to the ortho ground-scenery itself.
Also, before any changes are made, a backup of the original overlay is created automatically under `X-Plane 11/Transparency4Ortho/Backups`. And you are free to create manual backups ahead of time as well, of course. I never had any problem with destroyed overlays whatsoever in my testing, even in the very first development runs.

## Does it keep cars and trains visible?
Yes, these remain visible and animated.

## How can I rebuild / restore the Transparency4Ortho library?
Just run `Transparency4Ortho -r <path-to-xplane>`. This can also be useful after when a new version of Transparency4Ortho or X-Plane ships.

## I think I found a bug!
Great, please check if is [already reported](https://github.com/melb00m/Transparency4Ortho/issues) and if not, create a new one.

## How can I get in touch?
The best way would be to contact me on the [X-Plane.org Forums](https://forums.x-plane.org/index.php?/profile/726502-melb00m/).

