# Local visual workflow setup

The bundled `sd15-portrait-api.json` is a real API-format graph using standard ComfyUI nodes: checkpoint loader, CLIP text encoding, empty latent, KSampler, VAE decoder and SaveImage. It is not a bundled model.

1. Install ComfyUI following https://docs.comfy.org/installation/desktop/windows .
2. Obtain a compatible checkpoint using the model links in https://docs.comfy.org/tutorials/basic/text-to-image and review its terms.
3. Put the checkpoint in your ComfyUI checkpoints folder. Edit the graph's `ckpt_name` to its exact filename.
4. Run the workflow in ComfyUI first, using its API-format import if supported, or reconstruct the documented standard graph and export API format.
5. In Jenga Setup select this JSON, enable images, keep the correct loopback endpoint, then choose Balanced for a new project.

Tokens replaced in string values: `{{PROMPT}}`, `{{NEGATIVE}}`, `{{PREFIX}}`. Sampler `seed` / `noise_seed` values are randomized for a new generation. No arbitrary code or custom nodes are installed by Jenga.

For text-to-video choose a model/workflow from the current official ComfyUI examples, install that workflow's dependencies independently, and export its API graph. The adapter supports saved image outputs and MP4/WebM videos exposed in `history` as `images`, `gifs`, or `videos`. A workflow must save output rather than only preview it. Graphs using a different output protocol need provider adaptation; support is not universal.

Image-to-video/image-to-image conditioning, video model auto-installation and multi-candidate scoring are not implemented. There is no fake bundled video workflow. Video-model RAM/VRAM/storage requirements and model licences must be checked for the actual workflow you choose. Start with still images.

The application removes its own queued prompt on cancellation but deliberately does not interrupt an active shared ComfyUI server. A running generation may finish after cancellation. Use ComfyUI itself to stop it if required.
